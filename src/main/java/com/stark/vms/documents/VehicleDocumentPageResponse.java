package com.stark.vms.documents;

import org.springframework.data.domain.Page;

import java.util.List;

public record VehicleDocumentPageResponse(List<VehicleDocumentResponse> content,
		int page, int size, long totalElements, int totalPages) {
	public static VehicleDocumentPageResponse from(Page<VehicleDocumentResponse> page) {
		return new VehicleDocumentPageResponse(page.getContent(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}
}
