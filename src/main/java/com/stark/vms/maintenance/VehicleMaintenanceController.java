package com.stark.vms.maintenance;

import com.stark.vms.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles/{vehicleNumber}/maintenance")
public class VehicleMaintenanceController {
	private final VehicleMaintenanceService maintenance;

	public VehicleMaintenanceController(VehicleMaintenanceService maintenance) {
		this.maintenance = maintenance;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<VehicleMaintenanceResponse>> create(
			@PathVariable String vehicleNumber, @Valid @RequestBody MaintenanceRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
				"Maintenance record created successfully", maintenance.create(vehicleNumber, request)));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<VehicleMaintenancePageResponse>> history(
			@PathVariable String vehicleNumber,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ResponseEntity.ok(ApiResponse.success("Maintenance history retrieved successfully",
				maintenance.history(vehicleNumber, page, size)));
	}
}
