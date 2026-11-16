package com.stark.vms.documents;

import java.time.Instant;
import java.time.LocalDate;

public record VehicleDocumentResponse(Long id, Long vehicleId, DocumentType documentType,
		String filename, String contentType, long sizeBytes, LocalDate expiryDate,
		ExpiryState expiryState, Instant uploadedAt, boolean latest) {
}
