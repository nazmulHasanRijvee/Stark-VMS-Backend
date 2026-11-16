package com.stark.vms.documents;

import com.stark.vms.common.error.DocumentNotFoundException;
import com.stark.vms.common.error.DomainValidationException;
import com.stark.vms.common.error.StorageUnavailableException;
import com.stark.vms.common.error.VehicleNotFoundException;
import com.stark.vms.vehicle.Vehicle;
import com.stark.vms.vehicle.VehicleRepository;
import com.stark.vms.vehicle.VehicleService;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class VehicleDocumentService {
	private static final Logger logger = LoggerFactory.getLogger(VehicleDocumentService.class);
	private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
	private final VehicleRepository vehicles;
	private final VehicleDocumentRepository documents;
	private final DocumentStorage storage;
	private final DocumentExpiryPolicy expiryPolicy;
	private final Clock clock;
	private final int signedUrlTtlSeconds;

	public VehicleDocumentService(VehicleRepository vehicles, VehicleDocumentRepository documents,
			DocumentStorage storage, DocumentExpiryPolicy expiryPolicy, Clock clock,
			@Value("${app.supabase.signed-url-ttl-seconds:300}") int signedUrlTtlSeconds) {
		this.vehicles = vehicles;
		this.documents = documents;
		this.storage = storage;
		this.expiryPolicy = expiryPolicy;
		this.clock = clock;
		this.signedUrlTtlSeconds = signedUrlTtlSeconds;
	}

	@Transactional
	public VehicleDocumentResponse upload(String vehicleNumber, MultipartFile file,
			String documentTypeValue, LocalDate expiryDate) {
		Vehicle vehicle = findVehicle(vehicleNumber);
		DocumentType type = DocumentType.fromDisplayValue(documentTypeValue == null ? "" : documentTypeValue.strip());
		if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_BYTES) {
			throw new DomainValidationException("Choose a non-empty PDF, JPEG, or PNG file up to 10 MB.");
		}
		byte[] bytes;
		try {
			bytes = file.getBytes();
		} catch (IOException exception) {
			throw new DomainValidationException("The uploaded file could not be read.");
		}
		if (bytes.length == 0 || bytes.length > MAX_FILE_BYTES) {
			throw new DomainValidationException("Choose a non-empty PDF, JPEG, or PNG file up to 10 MB.");
		}
		String verifiedContentType = verifyContentType(bytes);
		String displayFilename = sanitizeFilename(file.getOriginalFilename());
		String objectKey = "vehicles/" + vehicle.getId() + "/documents/" + UUID.randomUUID();
		storage.upload(objectKey, bytes, verifiedContentType);
		VehicleDocument saved;
		try {
			saved = documents.saveAndFlush(new VehicleDocument(vehicle, type, displayFilename, objectKey,
					verifiedContentType, bytes.length, expiryDate, clock.instant()));
		} catch (RuntimeException persistenceFailure) {
			try {
				storage.deleteObjects(List.of(objectKey));
			} catch (RuntimeException cleanupFailure) {
				logger.error("Failed to compensate document storage after metadata persistence failure exceptionType={}",
						cleanupFailure.getClass().getSimpleName());
				throw new StorageUnavailableException();
			}
			throw persistenceFailure;
		}
		return toResponse(saved, true);
	}

	@Transactional(readOnly = true)
	public VehicleDocumentPageResponse list(String vehicleNumber, String documentTypeValue,
			String expiryStateValue, int page, int size) {
		validatePage(page, size);
		Vehicle vehicle = findVehicle(vehicleNumber);
		DocumentType type = parseOptionalType(documentTypeValue);
		ExpiryState expiryState = parseOptionalExpiryState(expiryStateValue);
		LocalDate today = expiryPolicy.today();
		LocalDate lastDay = today.plusDays(30);
		Specification<VehicleDocument> specification = (root, query, builder) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(builder.equal(root.get("vehicle").get("id"), vehicle.getId()));
			if (type != null) predicates.add(builder.equal(root.get("documentType"), type));
			if (expiryState != null) {
				switch (expiryState) {
					case EXPIRED -> predicates.add(builder.lessThan(root.get("expiryDate"), today));
					case EXPIRING_SOON -> predicates.add(builder.between(root.get("expiryDate"), today, lastDay));
					case VALID -> predicates.add(builder.greaterThan(root.get("expiryDate"), lastDay));
					case NO_EXPIRY_DATE -> predicates.add(builder.isNull(root.get("expiryDate")));
				}
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
		Page<VehicleDocument> result = documents.findAll(specification, PageRequest.of(page, size,
				Sort.by(Sort.Order.desc("uploadedAt"), Sort.Order.desc("id"))));
		Set<Long> latestIds = new HashSet<>(documents.findLatestIds(vehicle.getId()));
		Page<VehicleDocumentResponse> responses = result.map(document -> toResponse(document, latestIds.contains(document.getId())));
		return VehicleDocumentPageResponse.from(responses);
	}

	@Transactional(readOnly = true)
	public DocumentDownloadResponse createDownload(String vehicleNumber, Long documentId) {
		Vehicle vehicle = findVehicle(vehicleNumber);
		VehicleDocument document = documents.findByVehicle_IdAndId(vehicle.getId(), documentId)
				.orElseThrow(DocumentNotFoundException::new);
		String url = storage.createSignedDownloadUrl(document.getStorageObjectKey(), signedUrlTtlSeconds);
		return new DocumentDownloadResponse(document.getId(), url, signedUrlTtlSeconds);
	}

	@Transactional
	public void deleteForVehicle(Vehicle vehicle) {
		List<String> objectKeys = documents.findStorageObjectKeysByVehicleId(vehicle.getId());
		if (objectKeys.isEmpty()) return;
		storage.deleteObjects(objectKeys);
		documents.deleteAllForVehicle(vehicle.getId());
	}

	private Vehicle findVehicle(String vehicleNumber) {
		String normalized = VehicleService.normalizeVehicleNumber(vehicleNumber);
		return vehicles.findByVehicleNumber(normalized).orElseThrow(VehicleNotFoundException::new);
	}

	private VehicleDocumentResponse toResponse(VehicleDocument document, boolean latest) {
		return new VehicleDocumentResponse(document.getId(), document.getVehicle().getId(),
				document.getDocumentType(), document.getDisplayFilename(), document.getContentType(),
				document.getFileSizeBytes(), document.getExpiryDate(), expiryPolicy.state(document.getExpiryDate()),
				document.getUploadedAt(), latest);
	}

	private static String verifyContentType(byte[] bytes) {
		if (bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D'
				&& bytes[3] == 'F' && bytes[4] == '-') return "application/pdf";
		if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
				&& (bytes[2] & 0xff) == 0xff) return "image/jpeg";
		if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
				&& bytes[3] == 'G' && bytes[4] == 0x0d && bytes[5] == 0x0a
				&& bytes[6] == 0x1a && bytes[7] == 0x0a) return "image/png";
		throw new DomainValidationException("The file content must be a valid PDF, JPEG, or PNG.");
	}

	private static String sanitizeFilename(String original) {
		String filename = original == null ? "document" : original.replace('\\', '/');
		int slash = filename.lastIndexOf('/');
		if (slash >= 0) filename = filename.substring(slash + 1);
		filename = filename.replaceAll("[\\p{Cntrl}<>:\"|?*]", "_").strip();
		while (filename.startsWith(".")) filename = filename.substring(1);
		if (filename.isBlank()) filename = "document";
		return filename.length() > 255 ? filename.substring(filename.length() - 255) : filename;
	}

	private static DocumentType parseOptionalType(String value) {
		return value == null || value.isBlank() ? null : DocumentType.fromDisplayValue(value.strip());
	}

	private static ExpiryState parseOptionalExpiryState(String value) {
		if (value == null || value.isBlank()) return null;
		try {
			return ExpiryState.valueOf(value.strip().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw new DomainValidationException("Expiry state must be EXPIRED, EXPIRING_SOON, VALID, or NO_EXPIRY_DATE.");
		}
	}

	private static void validatePage(int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new DomainValidationException("Page must be zero or greater and page size must be between 1 and 100.");
		}
	}
}
