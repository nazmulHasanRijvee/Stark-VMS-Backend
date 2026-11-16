package com.stark.vms.vehicle;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long>, JpaSpecificationExecutor<Vehicle>,
		VehicleQueryRepository {

	@EntityGraph(attributePaths = "currentDriver")
	Optional<Vehicle> findByVehicleNumber(String vehicleNumber);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from Vehicle v where v.vehicleNumber = :vehicleNumber")
	Optional<Vehicle> findLockedByVehicleNumber(@Param("vehicleNumber") String vehicleNumber);

	boolean existsByCurrentDriver_Id(Long driverId);

	Optional<Vehicle> findByCurrentDriver_Id(Long driverId);

	boolean existsByVehicleNumber(String vehicleNumber);

	boolean existsByVehicleNumberAndIdNot(String vehicleNumber, Long id);

	long countByStatus(VehicleStatus status);
}
