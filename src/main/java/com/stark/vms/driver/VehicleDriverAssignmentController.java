package com.stark.vms.driver;

import com.stark.vms.common.api.ApiResponse;
import com.stark.vms.vehicle.VehicleResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/vehicles/{vehicleNumber}/driver")
public class VehicleDriverAssignmentController {
	private final DriverService drivers;

	public VehicleDriverAssignmentController(DriverService drivers) {
		this.drivers = drivers;
	}

	@PutMapping
	public ResponseEntity<ApiResponse<VehicleResponse>> assign(
			@PathVariable String vehicleNumber, @Valid @RequestBody DriverAssignmentRequest request) {
		return ResponseEntity.ok(ApiResponse.success("Driver assigned successfully",
				drivers.assignToVehicle(vehicleNumber, request.driverId())));
	}

	@DeleteMapping
	public ResponseEntity<ApiResponse<Map<String, Object>>> unassign(@PathVariable String vehicleNumber) {
		drivers.unassignFromVehicle(vehicleNumber);
		return ResponseEntity.ok(ApiResponse.success("Driver unassigned successfully", Map.of()));
	}
}
