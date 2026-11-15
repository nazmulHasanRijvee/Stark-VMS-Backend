package com.stark.vms.vehicle;

import com.stark.vms.documents.DocumentExpiryPolicy;
import com.stark.vms.documents.VehicleDocumentRepository;
import com.stark.vms.maintenance.VehicleMaintenanceRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class DashboardStatsService {

	private final VehicleRepository vehicles;
	private final VehicleDocumentRepository documents;
	private final VehicleMaintenanceRepository maintenance;
	private final DocumentExpiryPolicy expiryPolicy;
	private final Clock clock;
	private final ZoneId businessZone;

	public DashboardStatsService(VehicleRepository vehicles, VehicleDocumentRepository documents,
			VehicleMaintenanceRepository maintenance, DocumentExpiryPolicy expiryPolicy,
			Clock clock, ZoneId businessZone) {
		this.vehicles = vehicles;
		this.documents = documents;
		this.maintenance = maintenance;
		this.expiryPolicy = expiryPolicy;
		this.clock = clock;
		this.businessZone = businessZone;
	}

	@Transactional(readOnly = true)
	public DashboardStats getStats() {
		LocalDate today = expiryPolicy.today();
		LocalDate through = today.plusDays(30);
		LocalDate monthStart = LocalDate.now(clock.withZone(businessZone)).withDayOfMonth(1);
		LocalDate nextMonth = monthStart.plusMonths(1);
		var expiryAttention = documents.findLatestExpiringSoon(today, through, PageRequest.of(0, 10)).stream()
				.map(document -> new DashboardStats.DocumentExpiryAttention(document.getId(),
						document.getVehicle().getVehicleNumber(), document.getDocumentType(), document.getExpiryDate()))
				.toList();
		var recentMaintenance = maintenance.findRecent(PageRequest.of(0, 5)).stream()
				.map(DashboardStats.RecentMaintenance::from).toList();
		return new DashboardStats(vehicles.count(),
				vehicles.countByStatus(VehicleStatus.ACTIVE),
				vehicles.countByStatus(VehicleStatus.INACTIVE),
				vehicles.countByStatus(VehicleStatus.MAINTENANCE),
				documents.countLatestExpired(today),
				documents.countLatestExpiringSoon(today, through),
				expiryAttention,
				maintenance.countInPeriod(monthStart, nextMonth),
				maintenance.totalCostInPeriod(monthStart, nextMonth),
				recentMaintenance);
	}
}
