package com.stark.vms.driver;

import com.stark.vms.common.error.DomainValidationException;
import com.stark.vms.common.error.UniqueConstraintViolation;
import com.stark.vms.common.error.VehicleNotFoundException;
import com.stark.vms.vehicle.Vehicle;
import com.stark.vms.vehicle.VehicleRepository;
import com.stark.vms.vehicle.VehicleResponse;
import com.stark.vms.vehicle.VehicleService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DriverService {
	private static final List<String> SORT_FIELDS = List.of("name", "licenseExpiry", "status", "createdAt");
	private final DriverRepository drivers;
	private final VehicleRepository vehicles;

	public DriverService(DriverRepository drivers, VehicleRepository vehicles) {
		this.drivers = drivers;
		this.vehicles = vehicles;
	}

	@Transactional
	public DriverResponse create(DriverRequest request) {
		DriverRequest normalized = normalize(request);
		String license = Driver.normalizeLicenseNumber(normalized.licenseNumber());
		if (drivers.existsByLicenseNumber(license)) throw new DuplicateDriverLicenseException();
		return saveOrTranslateDuplicate(new Driver(normalized.name(), normalized.phone(), license,
				normalized.licenseExpiry(), normalized.status()));
	}

	@Transactional(readOnly = true)
	public DriverResponse findById(Long driverId) {
		return DriverResponse.from(drivers.findDetailsById(driverId).orElseThrow(DriverNotFoundException::new));
	}

	@Transactional
	public DriverResponse update(Long driverId, DriverRequest request) {
		DriverRequest normalized = normalize(request);
		Driver driver = findLocked(driverId);
		String license = Driver.normalizeLicenseNumber(normalized.licenseNumber());
		if (!license.equals(driver.getLicenseNumber())
				&& drivers.existsByLicenseNumberAndIdNot(license, driverId)) {
			throw new DuplicateDriverLicenseException();
		}
		if (normalized.status() == DriverStatus.INACTIVE && vehicles.existsByCurrentDriver_Id(driverId)) {
			throw new DriverConflictException("Unassign this driver before changing the status to Inactive.");
		}
		driver.updateDetails(normalized.name(), normalized.phone(), license,
				normalized.licenseExpiry(), normalized.status());
		return DriverResponse.from(driver);
	}

	@Transactional
	public void delete(Long driverId) {
		Driver driver = findLocked(driverId);
		if (vehicles.existsByCurrentDriver_Id(driverId)) {
			throw new DriverConflictException("Unassign this driver before deleting the driver.");
		}
		try {
			drivers.delete(driver);
			drivers.flush();
		} catch (DataIntegrityViolationException exception) {
			throw new DriverConflictException("Unassign this driver before deleting the driver.");
		}
	}

	@Transactional(readOnly = true)
	public DriverPageResponse search(String search, String status, int page, int size,
			String sortBy, String sortDirection) {
		if (page < 0) throw new DomainValidationException("Page must be zero or greater.");
		if (size < 1 || size > 100) throw new DomainValidationException("Page size must be between 1 and 100.");
		if (sortBy != null && !sortBy.isBlank() && !SORT_FIELDS.contains(sortBy)) {
			throw new DomainValidationException("Unsupported sort field.");
		}
		String direction = sortDirection == null || sortDirection.isBlank()
				? "asc" : sortDirection.strip().toLowerCase(Locale.ROOT);
		if (!direction.equals("asc") && !direction.equals("desc")) {
			throw new DomainValidationException("Sort direction must be asc or desc.");
		}
		if ((sortBy == null || sortBy.isBlank()) && sortDirection != null && !sortDirection.isBlank()) {
			throw new DomainValidationException("A sort field is required when a sort direction is supplied.");
		}

		DriverStatus requestedStatus = parseStatus(status);
		String normalizedSearch = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
		String pattern = "%" + escapeLike(normalizedSearch) + "%";
		Specification<Driver> specification = (root, query, builder) -> {
			List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
			if (!normalizedSearch.isEmpty()) {
				Join<Driver, Vehicle> assignedVehicle = root.join("assignedVehicle", JoinType.LEFT);
				predicates.add(builder.or(
						builder.like(builder.lower(root.get("name")), pattern, '!'),
						builder.like(builder.lower(root.get("phone")), pattern, '!'),
						builder.like(builder.lower(root.get("licenseNumber")), pattern, '!'),
						builder.like(builder.lower(assignedVehicle.get("vehicleNumber")), pattern, '!')));
			}
			if (requestedStatus != null) predicates.add(builder.equal(root.get("status"), requestedStatus));
			return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
		};

		Sort sort = sortBy == null || sortBy.isBlank()
				? Sort.by(Sort.Order.desc("createdAt"))
				: Sort.by("desc".equals(direction) ? Sort.Order.desc(sortBy) : Sort.Order.asc(sortBy));
		Page<Driver> result = drivers.findAll(specification,
				PageRequest.of(page, size, sort.and(Sort.by(Sort.Order.asc("id")))));
		return DriverPageResponse.from(result);
	}

	@Transactional
	public VehicleResponse assignToVehicle(String vehicleNumber, Long driverId) {
		if (driverId == null) throw new DomainValidationException("Driver ID is required.");
		String normalizedNumber = VehicleService.normalizeVehicleNumber(vehicleNumber);
		Vehicle vehicle = vehicles.findLockedByVehicleNumber(normalizedNumber)
				.orElseThrow(VehicleNotFoundException::new);
		Driver driver = findLocked(driverId);
		if (driver.getStatus() != DriverStatus.ACTIVE) {
			throw new DriverConflictException("Only Active drivers can be assigned to a vehicle.");
		}
		Vehicle otherVehicle = vehicles.findByCurrentDriver_Id(driverId).orElse(null);
		if (otherVehicle != null && !otherVehicle.getId().equals(vehicle.getId())) {
			throw new DriverConflictException("This driver is already assigned to another vehicle.");
		}
		vehicle.setCurrentDriver(driver);
		try {
			return VehicleResponse.from(vehicles.saveAndFlush(vehicle));
		} catch (DataIntegrityViolationException exception) {
			if (UniqueConstraintViolation.isUniqueConstraintViolation(exception)) {
				throw new DriverConflictException("This driver is already assigned to another vehicle.");
			}
			throw exception;
		}
	}

	@Transactional
	public void unassignFromVehicle(String vehicleNumber) {
		String normalizedNumber = VehicleService.normalizeVehicleNumber(vehicleNumber);
		Vehicle vehicle = vehicles.findLockedByVehicleNumber(normalizedNumber)
				.orElseThrow(VehicleNotFoundException::new);
		vehicle.setCurrentDriver(null);
		vehicles.saveAndFlush(vehicle);
	}

	private Driver findLocked(Long driverId) {
		if (driverId == null || driverId < 1) throw new DomainValidationException("Driver ID must be positive.");
		return drivers.findLockedById(driverId).orElseThrow(DriverNotFoundException::new);
	}

	private DriverResponse saveOrTranslateDuplicate(Driver driver) {
		try {
			return DriverResponse.from(drivers.saveAndFlush(driver));
		} catch (DataIntegrityViolationException exception) {
			if (UniqueConstraintViolation.isUniqueConstraintViolation(exception)) {
				throw new DuplicateDriverLicenseException();
			}
			throw exception;
		}
	}

	private static DriverRequest normalize(DriverRequest request) {
		if (request == null) throw new DomainValidationException("Driver details are required.");
		String name = clean(request.name(), 120, "Name");
		String phone = clean(request.phone(), 32, "Phone");
		String license = clean(request.licenseNumber(), 64, "License number");
		if (request.licenseExpiry() == null) throw new DomainValidationException("License expiry date is required.");
		if (request.status() == null) throw new DomainValidationException("A supported driver status is required.");
		return new DriverRequest(name, phone, Driver.normalizeLicenseNumber(license), request.licenseExpiry(), request.status());
	}

	private static String clean(String value, int maxLength, String label) {
		if (value == null || value.isBlank() || value.strip().length() > maxLength) {
			throw new DomainValidationException(label + " is required and must not exceed " + maxLength + " characters.");
		}
		return value.strip();
	}

	private static DriverStatus parseStatus(String status) {
		if (status == null || status.isBlank()) return null;
		try {
			return DriverStatus.fromDisplayValue(status.strip());
		} catch (DomainValidationException exception) {
			throw exception;
		}
	}

	private static String escapeLike(String value) {
		return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
	}
}
