package com.stark.vms.maintenance;

import com.stark.vms.common.error.DomainValidationException;
import com.stark.vms.common.error.VehicleNotFoundException;
import com.stark.vms.vehicle.Vehicle;
import com.stark.vms.vehicle.VehicleRepository;
import com.stark.vms.vehicle.VehicleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;

@Service
public class VehicleMaintenanceService {
	private final VehicleRepository vehicles;
	private final VehicleMaintenanceRepository records;
	private final Clock clock;

	public VehicleMaintenanceService(VehicleRepository vehicles, VehicleMaintenanceRepository records, Clock clock) {
		this.vehicles = vehicles;
		this.records = records;
		this.clock = clock;
	}

	@Transactional
	public VehicleMaintenanceResponse create(String vehicleNumber, MaintenanceRequest request) {
		if (request == null || request.maintenanceDate() == null || request.cost() == null
				|| request.cost().compareTo(BigDecimal.ZERO) < 0 || request.cost().scale() > 2
				|| request.maintenanceType() == null || request.maintenanceType().isBlank()
				|| request.maintenanceType().strip().length() > 128
				|| request.workshop() == null || request.workshop().isBlank()
				|| request.workshop().strip().length() > 128
				|| (request.notes() != null && request.notes().length() > 2000)) {
			throw new DomainValidationException("Maintenance details are invalid.");
		}
		Vehicle vehicle = findVehicle(vehicleNumber);
		VehicleMaintenanceRecord record = new VehicleMaintenanceRecord(vehicle, request.maintenanceDate(),
				request.maintenanceType().strip(), request.cost(), request.workshop().strip(),
				request.notes() == null || request.notes().isBlank() ? null : request.notes().strip(),
				clock.instant());
		return VehicleMaintenanceResponse.from(records.saveAndFlush(record));
	}

	@Transactional(readOnly = true)
	public VehicleMaintenancePageResponse history(String vehicleNumber, int page, int size) {
		validatePage(page, size);
		Vehicle vehicle = findVehicle(vehicleNumber);
		Sort sort = Sort.by(Sort.Order.desc("maintenanceDate"), Sort.Order.desc("id"));
		Page<VehicleMaintenanceRecord> result = records.findAllByVehicle_Id(vehicle.getId(),
				PageRequest.of(page, size, sort));
		return VehicleMaintenancePageResponse.from(result);
	}

	private Vehicle findVehicle(String vehicleNumber) {
		String normalized = VehicleService.normalizeVehicleNumber(vehicleNumber);
		return vehicles.findByVehicleNumber(normalized).orElseThrow(VehicleNotFoundException::new);
	}

	static void validatePage(int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new DomainValidationException("Page must be zero or greater and page size must be between 1 and 100.");
		}
	}
}
