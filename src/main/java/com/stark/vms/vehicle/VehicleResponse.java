package com.stark.vms.vehicle;

public record VehicleResponse(
		Long id,
		String vehicleNumber,
		String companyName,
		String vehicleType,
		String brand,
		String model,
		VehicleStatus status,
		java.time.Instant createdAt,
		java.time.Instant updatedAt) {

	public static VehicleResponse from(Vehicle vehicle) {
		return new VehicleResponse(vehicle.getId(), vehicle.getVehicleNumber(), vehicle.getCompanyName(),
				vehicle.getVehicleType(), vehicle.getBrand(), vehicle.getModel(), vehicle.getStatus(),
				vehicle.getCreatedAt(), vehicle.getUpdatedAt());
	}
}
