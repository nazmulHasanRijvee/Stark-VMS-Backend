package com.stark.vms.documents;

import com.stark.vms.common.error.StorageUnavailableException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

@Component
public class SupabaseS3StorageClient implements DocumentStorage {
	private static final Logger logger = LoggerFactory.getLogger(SupabaseS3StorageClient.class);
	private static final int DELETE_BATCH_SIZE = 1000;

	private final String endpoint;
	private final String region;
	private final String accessKeyId;
	private final String secretAccessKey;
	private final String bucket;
	private final int signedUrlTtlSeconds;

	private volatile S3Client s3Client;
	private volatile S3Presigner s3Presigner;

	public SupabaseS3StorageClient(
			@Value("${app.supabase.s3-endpoint:}") String endpoint,
			@Value("${app.supabase.s3-region:}") String region,
			@Value("${app.supabase.s3-access-key-id:}") String accessKeyId,
			@Value("${app.supabase.s3-secret-access-key:}") String secretAccessKey,
			@Value("${app.supabase.bucket:}") String bucket,
			@Value("${app.supabase.signed-url-ttl-seconds:300}") int signedUrlTtlSeconds) {
		this.endpoint = clean(endpoint);
		this.region = clean(region);
		this.accessKeyId = clean(accessKeyId);
		this.secretAccessKey = clean(secretAccessKey);
		this.bucket = clean(bucket);
		this.signedUrlTtlSeconds = signedUrlTtlSeconds;
	}

	@Override
	public void upload(String objectKey, byte[] contents, String contentType) {
		ensureConfigured();
		ensureBucketAccessible();
		execute("upload", () -> s3Client().putObject(
				PutObjectRequest.builder()
						.bucket(bucket)
						.key(objectKey)
						.contentType(contentType)
						.contentLength((long) contents.length)
						.build(),
				RequestBody.fromBytes(contents)));
	}

	@Override
	public void deleteObjects(List<String> objectKeys) {
		if (objectKeys == null || objectKeys.isEmpty()) return;
		ensureConfigured();
		ensureBucketAccessible();

		for (int start = 0; start < objectKeys.size(); start += DELETE_BATCH_SIZE) {
			List<ObjectIdentifier> identifiers = objectKeys.subList(start,
							Math.min(start + DELETE_BATCH_SIZE, objectKeys.size()))
					.stream()
					.map(key -> ObjectIdentifier.builder().key(key).build())
					.toList();
			DeleteObjectsResponse response = execute("delete", () -> s3Client().deleteObjects(
					DeleteObjectsRequest.builder()
							.bucket(bucket)
							.delete(Delete.builder().objects(identifiers).quiet(true).build())
							.build()));
			if (!response.errors().isEmpty()) {
				logger.warn("Supabase S3 delete returned per-object errors count={}", response.errors().size());
				throw new StorageUnavailableException();
			}
		}
	}

	@Override
	public String createSignedDownloadUrl(String objectKey, int expiresInSeconds) {
		ensureConfigured();
		if (expiresInSeconds < 1 || expiresInSeconds > signedUrlTtlSeconds) {
			throw new StorageUnavailableException();
		}
		ensureBucketAccessible();

		PresignedGetObjectRequest presigned = execute("sign", () -> s3Presigner().presignGetObject(
				GetObjectPresignRequest.builder()
						.signatureDuration(Duration.ofSeconds(expiresInSeconds))
						.getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(objectKey).build())
						.build()));
		String signedUrl = presigned.url().toExternalForm();
		try {
			URI signedUri = URI.create(signedUrl);
			URI configuredEndpoint = endpointUri();
			if (signedUri.getHost() == null || configuredEndpoint.getHost() == null
					|| !signedUri.getHost().equalsIgnoreCase(configuredEndpoint.getHost())
					|| !signedUri.getScheme().equalsIgnoreCase(configuredEndpoint.getScheme())) {
				throw new StorageUnavailableException();
			}
			return signedUrl;
		} catch (IllegalArgumentException exception) {
			throw new StorageUnavailableException(exception);
		}
	}

	private void ensureBucketAccessible() {
		execute("bucket-check", () -> s3Client().headBucket(
				HeadBucketRequest.builder().bucket(bucket).build()));
	}

	private <T> T execute(String operation, Supplier<T> request) {
		try {
			return request.get();
		} catch (S3Exception exception) {
			logger.warn("Supabase S3 operation failed operation={} status={}",
					operation, exception.statusCode());
			throw new StorageUnavailableException(exception);
		} catch (SdkClientException exception) {
			logger.warn("Supabase S3 operation failed operation={} exceptionType={}",
					operation, exception.getClass().getSimpleName());
			throw new StorageUnavailableException(exception);
		} catch (StorageUnavailableException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			logger.warn("Supabase S3 operation failed operation={} exceptionType={}",
					operation, exception.getClass().getSimpleName());
			throw new StorageUnavailableException(exception);
		}
	}

	private S3Client s3Client() {
		S3Client current = s3Client;
		if (current != null) return current;
		synchronized (this) {
			if (s3Client == null) {
				s3Client = S3Client.builder()
						.endpointOverride(endpointUri())
						.region(Region.of(region))
						.credentialsProvider(credentialsProvider())
						.serviceConfiguration(s3Configuration())
						.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
						.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
						.build();
			}
			return s3Client;
		}
	}

	private S3Presigner s3Presigner() {
		S3Presigner current = s3Presigner;
		if (current != null) return current;
		synchronized (this) {
			if (s3Presigner == null) {
				s3Presigner = S3Presigner.builder()
						.endpointOverride(endpointUri())
						.region(Region.of(region))
						.credentialsProvider(credentialsProvider())
						.serviceConfiguration(s3Configuration())
						.build();
			}
			return s3Presigner;
		}
	}

	private AwsCredentialsProvider credentialsProvider() {
		return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
	}

	private static S3Configuration s3Configuration() {
		return S3Configuration.builder()
				.pathStyleAccessEnabled(true)
				.chunkedEncodingEnabled(false)
				.build();
	}

	private void ensureConfigured() {
		if (endpoint.isBlank() || region.isBlank() || accessKeyId.isBlank()
				|| secretAccessKey.isBlank() || bucket.isBlank()
				|| signedUrlTtlSeconds < 1 || signedUrlTtlSeconds > 3600) {
			throw new StorageUnavailableException();
		}
		endpointUri();
	}

	private URI endpointUri() {
		try {
			URI uri = URI.create(endpoint);
			String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
			boolean loopback = "localhost".equalsIgnoreCase(uri.getHost())
					|| "127.0.0.1".equals(uri.getHost()) || "::1".equals(uri.getHost());
			if (!("https".equalsIgnoreCase(uri.getScheme())
					|| ("http".equalsIgnoreCase(uri.getScheme()) && loopback))
					|| uri.getHost() == null || uri.getUserInfo() != null
					|| uri.getRawQuery() != null || uri.getRawFragment() != null
					|| !path.endsWith("/storage/v1/s3")) {
				throw new IllegalArgumentException("Invalid Supabase S3 endpoint");
			}
			return URI.create(uri.getScheme() + "://" + uri.getRawAuthority() + path);
		} catch (IllegalArgumentException exception) {
			throw new StorageUnavailableException(exception);
		}
	}

	private static String clean(String value) {
		return value == null ? "" : value.strip();
	}

	@PreDestroy
	void closeClients() {
		S3Client client = s3Client;
		if (client != null) client.close();
		S3Presigner presigner = s3Presigner;
		if (presigner != null) presigner.close();
	}
}
