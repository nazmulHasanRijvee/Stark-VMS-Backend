package com.stark.vms.driver;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DriverRepository extends JpaRepository<Driver, Long>, JpaSpecificationExecutor<Driver> {
	boolean existsByLicenseNumber(String licenseNumber);

	boolean existsByLicenseNumberAndIdNot(String licenseNumber, Long id);

	@EntityGraph(attributePaths = "assignedVehicle")
	@Query("select d from Driver d where d.id = :driverId")
	Optional<Driver> findDetailsById(@Param("driverId") Long driverId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from Driver d where d.id = :driverId")
	Optional<Driver> findLockedById(@Param("driverId") Long driverId);

	@Override
	@EntityGraph(attributePaths = "assignedVehicle")
	Page<Driver> findAll(Specification<Driver> specification, Pageable pageable);
}
