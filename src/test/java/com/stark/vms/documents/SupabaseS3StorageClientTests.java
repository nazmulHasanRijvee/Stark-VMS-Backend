package com.stark.vms.documents;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupabaseS3StorageClientTests {
	private HttpServer server;
	private final List<CapturedRequest> requests = new CopyOnWriteArrayList<>();
	private SupabaseS3StorageClient storage;

	@BeforeEach
	void setUp() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/storage/v1/s3", this::handle);
		server.start();
		String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/storage/v1/s3";
		storage = new SupabaseS3StorageClient(endpoint, "local", "test-access-id",
				"test-secret-access-key", "private-bucket", 300);
	}

	@AfterEach
	void tearDown() {
		if (server != null) server.stop(0);
		if (storage != null) storage.closeClients();
	}

	@Test
	void uploadUsesS3EndpointAndSignsWithConfiguredS3Credentials() {
		storage.upload("vehicles/7/documents/test.pdf",
				"%PDF-1.7 test".getBytes(StandardCharsets.UTF_8), "application/pdf");

		assertEquals(2, requests.size());
		assertEquals("HEAD", requests.get(0).method());
		assertEquals("/storage/v1/s3/private-bucket", requests.get(0).path());
		assertEquals("PUT", requests.get(1).method());
		assertEquals("/storage/v1/s3/private-bucket/vehicles/7/documents/test.pdf",
				requests.get(1).path());
		assertEquals("application/pdf", requests.get(1).contentType());
		assertTrue(requests.get(1).authorization().startsWith("AWS4-HMAC-SHA256 "));
		assertTrue(requests.get(1).authorization().contains("Credential=test-access-id/"));
	}

	@Test
	void signedDownloadUsesShortLivedS3PresignedUrl() {
		String url = storage.createSignedDownloadUrl("vehicles/7/documents/test.pdf", 120);

		assertTrue(url.startsWith("http://127.0.0.1:" + server.getAddress().getPort()
				+ "/storage/v1/s3/private-bucket/vehicles/7/documents/test.pdf?"));
		assertTrue(url.contains("X-Amz-Algorithm=AWS4-HMAC-SHA256"));
		assertTrue(url.contains("X-Amz-Expires=120"));
		assertEquals(1, requests.size());
		assertEquals("HEAD", requests.get(0).method());
	}

	@Test
	void deletesObjectsUsingTheS3DeleteObjectsOperation() {
		storage.deleteObjects(List.of("vehicles/7/documents/first.pdf", "vehicles/7/documents/second.pdf"));

		assertEquals(2, requests.size());
		assertEquals("HEAD", requests.get(0).method());
		assertEquals("POST", requests.get(1).method());
		assertEquals("/storage/v1/s3/private-bucket", requests.get(1).path());
		assertTrue(requests.get(1).query().contains("delete"));
	}

	@Test
	void missingS3ConfigurationReturnsStorageUnavailableWithoutCreatingAClient() {
		SupabaseS3StorageClient unconfigured = new SupabaseS3StorageClient(
				"", "", "", "", "private-bucket", 300);
		try {
			assertThrows(com.stark.vms.common.error.StorageUnavailableException.class,
					() -> unconfigured.upload("key", new byte[]{1}, "application/octet-stream"));
			assertTrue(requests.isEmpty());
		} finally {
			unconfigured.closeClients();
		}
	}

	private void handle(HttpExchange exchange) throws IOException {
		String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
		String authorization = exchange.getRequestHeaders().getFirst("Authorization");
		requests.add(new CapturedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
				exchange.getRequestURI().getRawQuery(), contentType, authorization));
		exchange.getRequestBody().readAllBytes();
		if ("POST".equals(exchange.getRequestMethod())) {
			byte[] response = "<DeleteResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"></DeleteResult>"
					.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/xml");
			exchange.sendResponseHeaders(200, response.length);
			exchange.getResponseBody().write(response);
		} else {
			exchange.sendResponseHeaders(200, -1);
		}
		exchange.close();
	}

	private record CapturedRequest(String method, String path, String query,
			String contentType, String authorization) {
	}
}
