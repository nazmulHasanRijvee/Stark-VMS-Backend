package com.stark.vms.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long>, JpaSpecificationExecutor<Vehicle> {

	Optional<Vehicle> findByVehicleNumber(String vehicleNumber);

	boolean existsByVehicleNumber(String vehicleNumber);

	boolean existsByVehicleNumberAndIdNot(String vehicleNumber, Long id);

	long countByStatus(VehicleStatus status);
}
