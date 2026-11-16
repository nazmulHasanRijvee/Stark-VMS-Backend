package com.stark.vms.driver;

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
@RequestMapping("/api/v1/drivers")
public class DriverController {
	private final DriverService drivers;

	public DriverController(DriverService drivers) {
		this.drivers = drivers;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<DriverResponse>> create(@Valid @RequestBody DriverRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success("Driver created successfully", drivers.create(request)));
	}

	@GetMapping("/{driverId}")
	public ResponseEntity<ApiResponse<DriverResponse>> get(@PathVariable Long driverId) {
		return ResponseEntity.ok(ApiResponse.success("Driver retrieved successfully", drivers.findById(driverId)));
	}

	@PutMapping("/{driverId}")
	public ResponseEntity<ApiResponse<DriverResponse>> update(
			@PathVariable Long driverId, @Valid @RequestBody DriverRequest request) {
		return ResponseEntity.ok(ApiResponse.success("Driver updated successfully", drivers.update(driverId, request)));
	}

	@DeleteMapping("/{driverId}")
	public ResponseEntity<ApiResponse<Map<String, Object>>> delete(@PathVariable Long driverId) {
		drivers.delete(driverId);
		return ResponseEntity.ok(ApiResponse.success("Driver deleted successfully", Map.of()));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<DriverPageResponse>> list(
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size,
			@RequestParam(required = false) String sortBy,
			@RequestParam(required = false) String sortDirection) {
		return ResponseEntity.ok(ApiResponse.success("Drivers retrieved successfully",
				drivers.search(search, status, page, size, sortBy, sortDirection)));
	}
}
