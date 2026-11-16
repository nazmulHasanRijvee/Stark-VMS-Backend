package com.stark.vms.driver;

import org.springframework.data.domain.Page;

import java.util.List;

public record DriverPageResponse(List<DriverResponse> content, int page, int size,
		long totalElements, int totalPages) {
	public static DriverPageResponse from(Page<Driver> page) {
		return new DriverPageResponse(page.getContent().stream().map(DriverResponse::from).toList(),
				page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
	}
}
