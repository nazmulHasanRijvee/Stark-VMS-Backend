package com.stark.vms.documents;

import com.stark.vms.common.error.StorageUnavailableException;
import com.stark.vms.vehicle.Vehicle;
import com.stark.vms.vehicle.VehicleRepository;
import com.stark.vms.vehicle.VehicleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleDocumentServiceTests {
	@Mock private VehicleRepository vehicles;
	@Mock private VehicleDocumentRepository documents;
	@Mock private DocumentStorage storage;

	private VehicleDocumentService service;

	@BeforeEach
	void setUp() {
		service = new VehicleDocumentService(vehicles, documents, storage,
				new DocumentExpiryPolicy(Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneId.of("UTC")),
						ZoneId.of("Asia/Dhaka")),
				Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneId.of("UTC")), 300);
		Vehicle vehicle = new Vehicle("TEST-1", "Stark", "Sedan", "Toyota", "Corolla", VehicleStatus.ACTIVE);
		ReflectionTestUtils.setField(vehicle, "id", 77L);
		when(vehicles.findByVehicleNumber("TEST-1")).thenReturn(Optional.of(vehicle));
	}

	@Test
	void removesUploadedObjectWhenMetadataPersistenceFails() {
		when(documents.saveAndFlush(any(VehicleDocument.class)))
				.thenThrow(new DataIntegrityViolationException("database unavailable"));
		MockMultipartFile file = new MockMultipartFile("file", "certificate.pdf", "application/pdf",
				"%PDF-1.7 content".getBytes(java.nio.charset.StandardCharsets.UTF_8));

		assertThrows(DataIntegrityViolationException.class,
				() -> service.upload("TEST-1", file, "Registration", null));
		verify(storage).upload(any(String.class), any(byte[].class), org.mockito.ArgumentMatchers.eq("application/pdf"));
		verify(storage).deleteObjects(anyList());
	}

	@Test
	void storageFailureDoesNotPersistDocumentMetadata() {
		org.mockito.Mockito.doThrow(new StorageUnavailableException()).when(storage)
				.upload(any(String.class), any(byte[].class), any(String.class));
		MockMultipartFile file = new MockMultipartFile("file", "certificate.pdf", "application/pdf",
				"%PDF-1.7 content".getBytes(java.nio.charset.StandardCharsets.UTF_8));

		assertThrows(StorageUnavailableException.class,
				() -> service.upload("TEST-1", file, "Registration", null));
		verify(documents, never()).saveAndFlush(any(VehicleDocument.class));
	}

	@Test
	void rejectsContentWithoutSupportedFileSignatureBeforeStorage() {
		MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf", "plain text".getBytes());
		assertThrows(RuntimeException.class, () -> service.upload("TEST-1", file, "Registration", null));
		verify(storage, never()).upload(any(String.class), any(byte[].class), any(String.class));
	}

	@Test
	void rejectsFilesAboveTenMegabytesBeforeStorage() {
		byte[] tooLarge = new byte[10 * 1024 * 1024 + 1];
		tooLarge[0] = '%';
		tooLarge[1] = 'P';
		tooLarge[2] = 'D';
		tooLarge[3] = 'F';
		tooLarge[4] = '-';
		MockMultipartFile file = new MockMultipartFile("file", "large.pdf", "application/pdf", tooLarge);
		assertThrows(RuntimeException.class, () -> service.upload("TEST-1", file, "Registration", null));
		verify(storage, never()).upload(any(String.class), any(byte[].class), any(String.class));
	}
}
