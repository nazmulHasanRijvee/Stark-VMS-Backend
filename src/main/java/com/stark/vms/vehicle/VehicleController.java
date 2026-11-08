package com.stark.vms.vehicle;

import com.stark.vms.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

	private final VehicleService vehicleService;

	public VehicleController(VehicleService vehicleService) {
		this.vehicleService = vehicleService;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<VehiclePageResponse>> list(
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		VehiclePageResponse result = vehicleService.search(search, status, page, size);
		return ResponseEntity.ok(ApiResponse.success("Vehicles retrieved successfully", result));
	}

	@GetMapping("/{vehicleNumber}")
	public ResponseEntity<ApiResponse<VehicleResponse>> get(@PathVariable String vehicleNumber) {
		return ResponseEntity.ok(ApiResponse.success("Vehicle retrieved successfully",
				vehicleService.findByNumber(vehicleNumber)));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<VehicleResponse>> create(@Valid @RequestBody VehicleRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success("Vehicle registered successfully", vehicleService.create(request)));
	}

	@PutMapping("/{vehicleNumber}")
	public ResponseEntity<ApiResponse<VehicleResponse>> update(
			@PathVariable String vehicleNumber, @Valid @RequestBody VehicleRequest request) {
		return ResponseEntity.ok(ApiResponse.success("Vehicle updated successfully",
				vehicleService.update(vehicleNumber, request)));
	}

	@DeleteMapping("/{vehicleNumber}")
	public ResponseEntity<ApiResponse<Map<String, Object>>> delete(@PathVariable String vehicleNumber) {
		vehicleService.delete(vehicleNumber);
		return ResponseEntity.ok(ApiResponse.success("Vehicle deleted successfully", Map.of()));
	}
}
