package com.stark.vms.vehicle;

import com.stark.vms.common.api.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

	private final DashboardStatsService dashboardStatsService;

	public DashboardController(DashboardStatsService dashboardStatsService) {
		this.dashboardStatsService = dashboardStatsService;
	}

	@GetMapping("/stats")
	public ResponseEntity<ApiResponse<DashboardStats>> stats() {
		return ResponseEntity.ok(ApiResponse.success("Dashboard statistics retrieved successfully",
				dashboardStatsService.getStats()));
	}
}
