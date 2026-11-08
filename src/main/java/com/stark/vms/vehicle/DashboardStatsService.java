package com.stark.vms.vehicle;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardStatsService {

	private final VehicleRepository vehicles;

	public DashboardStatsService(VehicleRepository vehicles) {
		this.vehicles = vehicles;
	}

	@Transactional(readOnly = true)
	public DashboardStats getStats() {
		return new DashboardStats(vehicles.count(),
				vehicles.countByStatus(VehicleStatus.ACTIVE),
				vehicles.countByStatus(VehicleStatus.IN_SERVICE),
				vehicles.countByStatus(VehicleStatus.INACTIVE));
	}
}
