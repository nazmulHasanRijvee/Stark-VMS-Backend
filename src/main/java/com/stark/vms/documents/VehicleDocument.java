package com.stark.vms.documents;

import com.stark.vms.vehicle.Vehicle;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "vehicle_documents")
public class VehicleDocument {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vehicle_id", nullable = false)
	private Vehicle vehicle;

	@Convert(converter = DocumentTypeConverter.class)
	@Column(name = "document_type", nullable = false, length = 40)
	private DocumentType documentType;

	@Column(name = "display_filename", nullable = false, length = 255)
	private String displayFilename;

	@Column(name = "storage_object_key", nullable = false, unique = true, length = 255)
	private String storageObjectKey;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Column(name = "file_size_bytes", nullable = false)
	private long fileSizeBytes;

	@Column(name = "expiry_date")
	private LocalDate expiryDate;

	@Column(name = "uploaded_at", nullable = false, updatable = false)
	private Instant uploadedAt;

	protected VehicleDocument() {
	}

	public VehicleDocument(Vehicle vehicle, DocumentType documentType, String displayFilename,
			String storageObjectKey, String contentType, long fileSizeBytes, LocalDate expiryDate, Instant uploadedAt) {
		this.vehicle = vehicle;
		this.documentType = documentType;
		this.displayFilename = displayFilename;
		this.storageObjectKey = storageObjectKey;
		this.contentType = contentType;
		this.fileSizeBytes = fileSizeBytes;
		this.expiryDate = expiryDate;
		this.uploadedAt = uploadedAt;
	}

	public Long getId() { return id; }
	public Vehicle getVehicle() { return vehicle; }
	public DocumentType getDocumentType() { return documentType; }
	public String getDisplayFilename() { return displayFilename; }
	public String getStorageObjectKey() { return storageObjectKey; }
	public String getContentType() { return contentType; }
	public long getFileSizeBytes() { return fileSizeBytes; }
	public LocalDate getExpiryDate() { return expiryDate; }
	public Instant getUploadedAt() { return uploadedAt; }
}
