package com.stark.vms.vehicle;

import com.stark.vms.driver.Driver;

public record VehicleResponse(
		Long id,
		String vehicleNumber,
		String companyName,
		String vehicleType,
		String brand,
		String model,
		VehicleStatus status,
		java.time.Instant createdAt,
		java.time.Instant updatedAt,
		AssignedDriver assignedDriver) {

	public record AssignedDriver(Long id, String name, String phone) {
		static AssignedDriver from(Driver driver) {
			return driver == null ? null : new AssignedDriver(driver.getId(), driver.getName(), driver.getPhone());
		}
	}

	public static VehicleResponse from(Vehicle vehicle) {
		return new VehicleResponse(vehicle.getId(), vehicle.getVehicleNumber(), vehicle.getCompanyName(),
				vehicle.getVehicleType(), vehicle.getBrand(), vehicle.getModel(), vehicle.getStatus(),
				vehicle.getCreatedAt(), vehicle.getUpdatedAt(), AssignedDriver.from(vehicle.getCurrentDriver()));
	}
}
