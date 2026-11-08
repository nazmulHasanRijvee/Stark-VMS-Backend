package com.stark.vms.vehicle;

import com.stark.vms.common.error.DomainValidationException;
import com.stark.vms.common.error.DuplicateVehicleNumberException;
import com.stark.vms.common.error.UniqueConstraintViolation;
import com.stark.vms.common.error.VehicleNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class VehicleService {

	private final VehicleRepository vehicles;

	public VehicleService(VehicleRepository vehicles) {
		this.vehicles = vehicles;
	}

	@Transactional
	public VehicleResponse create(VehicleRequest request) {
		validateRequest(request);
		String number = normalizeVehicleNumber(request.vehicleNumber());
		if (vehicles.existsByVehicleNumber(number)) {
			throw new DuplicateVehicleNumberException();
		}
		Vehicle vehicle = new Vehicle(number, clean(request.companyName()), clean(request.vehicleType()),
				clean(request.brand()), clean(request.model()), request.status());
		return saveOrTranslateDuplicate(vehicle);
	}

	@Transactional
	public VehicleResponse update(String currentVehicleNumber, VehicleRequest request) {
		validateRequest(request);
		String currentNumber = normalizeVehicleNumber(currentVehicleNumber);
		String newNumber = normalizeVehicleNumber(request.vehicleNumber());
		Vehicle vehicle = vehicles.findByVehicleNumber(currentNumber).orElseThrow(VehicleNotFoundException::new);
		if (!newNumber.equals(currentNumber)
				&& vehicles.existsByVehicleNumberAndIdNot(newNumber, vehicle.getId())) {
			throw new DuplicateVehicleNumberException();
		}
		vehicle.rename(newNumber);
		vehicle.updateDetails(clean(request.companyName()), clean(request.vehicleType()),
				clean(request.brand()), clean(request.model()), request.status());
		return saveOrTranslateDuplicate(vehicle);
	}

	@Transactional(readOnly = true)
	public VehicleResponse findByNumber(String vehicleNumber) {
		String normalized = normalizeVehicleNumber(vehicleNumber);
		return VehicleResponse.from(vehicles.findByVehicleNumber(normalized)
				.orElseThrow(VehicleNotFoundException::new));
	}

	@Transactional(readOnly = true)
	public List<VehicleResponse> findAll() {
		return vehicles.findAll().stream().map(VehicleResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public VehiclePageResponse search(String search, String status, int page, int size) {
		if (page < 0) {
			throw new DomainValidationException("Page must be zero or greater.");
		}
		if (size < 1 || size > 100) {
			throw new DomainValidationException("Page size must be between 1 and 100.");
		}

		List<Specification<Vehicle>> filters = new ArrayList<>();
		String normalizedSearch = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
		if (!normalizedSearch.isEmpty()) {
			String escapedSearch = normalizedSearch.replace("\\", "\\\\")
					.replace("%", "\\%").replace("_", "\\_");
			String pattern = "%" + escapedSearch + "%";
			filters.add((root, query, criteriaBuilder) -> {
				Predicate[] matches = new Predicate[] {
						criteriaBuilder.like(criteriaBuilder.lower(root.get("vehicleNumber")), pattern, '\\'),
						criteriaBuilder.like(criteriaBuilder.lower(root.get("companyName")), pattern, '\\'),
						criteriaBuilder.like(criteriaBuilder.lower(root.get("brand")), pattern, '\\'),
						criteriaBuilder.like(criteriaBuilder.lower(root.get("model")), pattern, '\\')
				};
				return criteriaBuilder.or(matches);
			});
		}
		if (status != null && !status.isBlank()) {
			VehicleStatus requestedStatus;
			try {
				requestedStatus = VehicleStatus.fromDisplayValue(status.strip());
			} catch (IllegalArgumentException exception) {
				throw new DomainValidationException("Status must be Active, In Service, or Inactive.");
			}
			filters.add((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), requestedStatus));
		}

		Specification<Vehicle> specification = filters.stream()
				.reduce(Specification::and).orElse((root, query, criteriaBuilder) -> criteriaBuilder.conjunction());
		Sort sort = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id"));
		Page<Vehicle> result = vehicles.findAll(specification, PageRequest.of(page, size, sort));
		return VehiclePageResponse.from(result);
	}

	@Transactional
	public void delete(String vehicleNumber) {
		Vehicle vehicle = vehicles.findByVehicleNumber(normalizeVehicleNumber(vehicleNumber))
				.orElseThrow(VehicleNotFoundException::new);
		vehicles.delete(vehicle);
	}

	public static String normalizeVehicleNumber(String vehicleNumber) {
		if (vehicleNumber == null || vehicleNumber.isBlank()) {
			throw new DomainValidationException("Vehicle number is required.");
		}
		String normalized = vehicleNumber.strip().toUpperCase(Locale.ROOT);
		if (normalized.length() > 64) {
			throw new DomainValidationException("Vehicle number must not exceed 64 characters.");
		}
		return normalized;
	}

	private VehicleResponse saveOrTranslateDuplicate(Vehicle vehicle) {
		try {
			return VehicleResponse.from(vehicles.saveAndFlush(vehicle));
		} catch (DataIntegrityViolationException exception) {
			if (UniqueConstraintViolation.isUniqueConstraintViolation(exception)) {
				throw new DuplicateVehicleNumberException();
			}
			throw exception;
		}
	}

	private static void validateRequest(VehicleRequest request) {
		if (request == null) {
			throw new DomainValidationException("Vehicle details are required.");
		}
		if (request.status() == null) {
			throw new DomainValidationException("A supported vehicle status is required.");
		}
		requireText(request.vehicleNumber(), 64, "Vehicle number");
		requireText(request.companyName(), 128, "Company name");
		requireText(request.vehicleType(), 64, "Vehicle type");
		requireText(request.brand(), 64, "Brand");
		requireText(request.model(), 64, "Model");
	}

	private static void requireText(String value, int maxLength, String fieldName) {
		if (value == null || value.isBlank() || value.strip().length() > maxLength) {
			throw new DomainValidationException(fieldName + " is required and must not exceed " + maxLength + " characters.");
		}
	}

	private static String clean(String value) {
		return value.strip();
	}
}
