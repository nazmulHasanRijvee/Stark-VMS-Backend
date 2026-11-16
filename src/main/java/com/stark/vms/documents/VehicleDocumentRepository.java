package com.stark.vms.documents;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VehicleDocumentRepository extends JpaRepository<VehicleDocument, Long>,
		JpaSpecificationExecutor<VehicleDocument> {

	Optional<VehicleDocument> findByVehicle_IdAndId(Long vehicleId, Long id);

	@Query("select d.storageObjectKey from VehicleDocument d where d.vehicle.id = :vehicleId order by d.uploadedAt desc, d.id desc")
	List<String> findStorageObjectKeysByVehicleId(@Param("vehicleId") Long vehicleId);

	@Modifying
	@Query("delete from VehicleDocument d where d.vehicle.id = :vehicleId")
	void deleteAllForVehicle(@Param("vehicleId") Long vehicleId);

	@Query("select d.id from VehicleDocument d where d.vehicle.id = :vehicleId and not exists "
			+ "(select n.id from VehicleDocument n where n.vehicle.id = d.vehicle.id "
			+ "and n.documentType = d.documentType and (n.uploadedAt > d.uploadedAt "
			+ "or (n.uploadedAt = d.uploadedAt and n.id > d.id)))")
	List<Long> findLatestIds(@Param("vehicleId") Long vehicleId);

	@Query("select count(d) from VehicleDocument d where d.expiryDate < :today and not exists "
			+ "(select n.id from VehicleDocument n where n.vehicle.id = d.vehicle.id "
			+ "and n.documentType = d.documentType and (n.uploadedAt > d.uploadedAt "
			+ "or (n.uploadedAt = d.uploadedAt and n.id > d.id)))")
	long countLatestExpired(@Param("today") LocalDate today);

	@Query("select count(d) from VehicleDocument d where d.expiryDate between :today and :lastDay "
			+ "and not exists (select n.id from VehicleDocument n where n.vehicle.id = d.vehicle.id "
			+ "and n.documentType = d.documentType and (n.uploadedAt > d.uploadedAt "
			+ "or (n.uploadedAt = d.uploadedAt and n.id > d.id)))")
	long countLatestExpiringSoon(@Param("today") LocalDate today, @Param("lastDay") LocalDate lastDay);

	@Query("select d from VehicleDocument d join fetch d.vehicle v where d.expiryDate between :today and :lastDay "
			+ "and not exists (select n.id from VehicleDocument n where n.vehicle.id = d.vehicle.id "
			+ "and n.documentType = d.documentType and (n.uploadedAt > d.uploadedAt "
			+ "or (n.uploadedAt = d.uploadedAt and n.id > d.id))) "
			+ "order by d.expiryDate asc, d.id asc")
	List<VehicleDocument> findLatestExpiringSoon(@Param("today") LocalDate today,
			@Param("lastDay") LocalDate lastDay, Pageable pageable);
}
