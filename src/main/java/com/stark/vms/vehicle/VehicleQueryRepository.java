package com.stark.vms.vehicle;

import org.springframework.data.domain.Page;

public interface VehicleQueryRepository {
	Page<Vehicle> search(String search, VehicleStatus status, int page, int size,
			String sortBy, String sortDirection);
}
