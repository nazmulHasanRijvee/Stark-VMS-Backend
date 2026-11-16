package com.stark.vms.maintenance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record VehicleMaintenanceResponse(
		Long id,
		String vehicleNumber,
		LocalDate maintenanceDate,
		String maintenanceType,
		BigDecimal cost,
		String workshop,
		String notes,
		Instant createdAt) {
	public static VehicleMaintenanceResponse from(VehicleMaintenanceRecord record) {
		return new VehicleMaintenanceResponse(record.getId(), record.getVehicle().getVehicleNumber(),
				record.getMaintenanceDate(), record.getMaintenanceType(), record.getCost(),
				record.getWorkshop(), record.getNotes(), record.getCreatedAt());
	}
}
