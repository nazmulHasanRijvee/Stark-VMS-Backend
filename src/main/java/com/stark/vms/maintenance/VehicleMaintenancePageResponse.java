package com.stark.vms.maintenance;

import org.springframework.data.domain.Page;

import java.util.List;

public record VehicleMaintenancePageResponse(List<VehicleMaintenanceResponse> content,
		int page, int size, long totalElements, int totalPages) {
	public static VehicleMaintenancePageResponse from(Page<VehicleMaintenanceRecord> page) {
		return new VehicleMaintenancePageResponse(page.getContent().stream()
				.map(VehicleMaintenanceResponse::from).toList(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}
}
