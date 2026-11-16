package com.stark.vms.driver;

import com.stark.vms.vehicle.Vehicle;

import java.time.Instant;
import java.time.LocalDate;

public record DriverResponse(
		Long id,
		String name,
		String phone,
		String licenseNumber,
		LocalDate licenseExpiry,
		DriverStatus status,
		Instant createdAt,
		Instant updatedAt,
		AssignedVehicle assignedVehicle) {
	public record AssignedVehicle(String vehicleNumber, String brand, String model) {
		static AssignedVehicle from(Vehicle vehicle) {
			return vehicle == null ? null : new AssignedVehicle(vehicle.getVehicleNumber(), vehicle.getBrand(), vehicle.getModel());
		}
	}

	public static DriverResponse from(Driver driver) {
		return new DriverResponse(driver.getId(), driver.getName(), driver.getPhone(), driver.getLicenseNumber(),
				driver.getLicenseExpiry(), driver.getStatus(), driver.getCreatedAt(), driver.getUpdatedAt(),
				AssignedVehicle.from(driver.getAssignedVehicle()));
	}
}
