package com.stark.vms.vehicle;

import com.stark.vms.documents.DocumentType;
import com.stark.vms.maintenance.VehicleMaintenanceRecord;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardStats(
		long totalVehicles,
		long activeVehicles,
		long inactiveVehicles,
		long maintenanceVehicles,
		long expiredDocuments,
		long expiringSoonDocuments,
		List<DocumentExpiryAttention> expiringSoon,
		long maintenanceRecordsThisMonth,
		BigDecimal maintenanceCostThisMonth,
		List<RecentMaintenance> recentMaintenance) {

	public record DocumentExpiryAttention(Long documentId, String vehicleNumber,
			DocumentType documentType, LocalDate expiryDate) {
	}

	public record RecentMaintenance(Long recordId, LocalDate maintenanceDate, String vehicleNumber,
			String maintenanceType, String workshop, BigDecimal cost) {
		public static RecentMaintenance from(VehicleMaintenanceRecord record) {
			return new RecentMaintenance(record.getId(), record.getMaintenanceDate(),
					record.getVehicle().getVehicleNumber(), record.getMaintenanceType(),
					record.getWorkshop(), record.getCost());
		}
	}
}
