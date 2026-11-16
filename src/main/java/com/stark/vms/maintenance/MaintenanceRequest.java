package com.stark.vms.maintenance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MaintenanceRequest(
		@NotNull LocalDate maintenanceDate,
		@NotBlank @Size(max = 128) String maintenanceType,
		@NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal cost,
		@NotBlank @Size(max = 128) String workshop,
		@Size(max = 2000) String notes) {
}
