package com.stark.vms.vehicle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VehicleRequest(
		@NotBlank @Size(max = 64) String vehicleNumber,
		@NotBlank @Size(max = 128) String companyName,
		@NotBlank @Size(max = 64) String vehicleType,
		@NotBlank @Size(max = 64) String brand,
		@NotBlank @Size(max = 64) String model,
		@NotNull VehicleStatus status) {
}
