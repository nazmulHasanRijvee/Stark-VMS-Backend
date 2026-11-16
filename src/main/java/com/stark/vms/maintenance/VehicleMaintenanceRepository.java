package com.stark.vms.maintenance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface VehicleMaintenanceRepository extends JpaRepository<VehicleMaintenanceRecord, Long> {
	Page<VehicleMaintenanceRecord> findAllByVehicle_Id(Long vehicleId, Pageable pageable);

	@Query("select count(m) from VehicleMaintenanceRecord m where m.maintenanceDate >= :start and m.maintenanceDate < :end")
	long countInPeriod(@Param("start") LocalDate start, @Param("end") LocalDate end);

	@Query("select coalesce(sum(m.cost), 0) from VehicleMaintenanceRecord m where m.maintenanceDate >= :start and m.maintenanceDate < :end")
	BigDecimal totalCostInPeriod(@Param("start") LocalDate start, @Param("end") LocalDate end);

	@Query("select m from VehicleMaintenanceRecord m join fetch m.vehicle order by m.maintenanceDate desc, m.id desc")
	List<VehicleMaintenanceRecord> findRecent(Pageable pageable);
}
