package com.stark.vms.documents;

import com.stark.vms.common.api.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/vehicles/{vehicleNumber}/documents")
public class VehicleDocumentController {
	private final VehicleDocumentService documents;

	public VehicleDocumentController(VehicleDocumentService documents) {
		this.documents = documents;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse<VehicleDocumentResponse>> upload(
			@PathVariable String vehicleNumber,
			@RequestParam("file") MultipartFile file,
			@RequestParam("documentType") String documentType,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Document uploaded successfully",
				documents.upload(vehicleNumber, file, documentType, expiryDate)));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<VehicleDocumentPageResponse>> list(
			@PathVariable String vehicleNumber,
			@RequestParam(required = false) String documentType,
			@RequestParam(required = false) String expiryState,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ResponseEntity.ok(ApiResponse.success("Vehicle documents retrieved successfully",
				documents.list(vehicleNumber, documentType, expiryState, page, size)));
	}

	@GetMapping("/{documentId}/download")
	public ResponseEntity<ApiResponse<DocumentDownloadResponse>> download(
			@PathVariable String vehicleNumber, @PathVariable Long documentId) {
		return ResponseEntity.ok(ApiResponse.success("Temporary document access created successfully",
				documents.createDownload(vehicleNumber, documentId)));
	}
}
