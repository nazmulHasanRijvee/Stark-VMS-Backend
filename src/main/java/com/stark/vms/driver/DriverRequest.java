package com.stark.vms.driver;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DriverRequest(
		@NotBlank @Size(max = 120) String name,
		@NotBlank @Size(max = 32) String phone,
		@NotBlank @Size(max = 64) String licenseNumber,
		@NotNull LocalDate licenseExpiry,
		@NotNull DriverStatus status) {
}
