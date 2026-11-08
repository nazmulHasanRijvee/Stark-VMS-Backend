package com.stark.vms.vehicle;

import org.springframework.data.domain.Page;

import java.util.List;

public record VehiclePageResponse(
		List<VehicleResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {

	public static VehiclePageResponse from(Page<Vehicle> vehiclePage) {
		return new VehiclePageResponse(
				vehiclePage.getContent().stream().map(VehicleResponse::from).toList(),
				vehiclePage.getNumber(), vehiclePage.getSize(),
				vehiclePage.getTotalElements(), vehiclePage.getTotalPages());
	}
}
