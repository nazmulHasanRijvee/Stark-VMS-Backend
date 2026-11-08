package com.stark.vms.vehicle;

import com.stark.vms.common.error.DuplicateVehicleNumberException;
import com.stark.vms.common.error.VehicleNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTests {

	@Mock
	private VehicleRepository vehicles;

	private VehicleService service;

	@BeforeEach
	void setUp() {
		service = new VehicleService(vehicles);
	}

	@Test
	void createTrimsAndUppercasesVehicleNumberAndTextFields() {
		when(vehicles.existsByVehicleNumber("DHAKA METRO GA-12-3456")).thenReturn(false);
		when(vehicles.saveAndFlush(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));

		VehicleResponse response = service.create(new VehicleRequest("  dhaka metro ga-12-3456 ",
				" Stark Logistics ", " Sedan ", " Toyota ", " Corolla ", VehicleStatus.ACTIVE));

		assertEquals("DHAKA METRO GA-12-3456", response.vehicleNumber());
		assertEquals("Stark Logistics", response.companyName());
		assertEquals("Sedan", response.vehicleType());
		verify(vehicles).saveAndFlush(any(Vehicle.class));
	}

	@Test
	void createRejectsDuplicateBeforeSaving() {
		when(vehicles.existsByVehicleNumber("DHAKA METRO GA-12-3456")).thenReturn(true);

		assertThrows(DuplicateVehicleNumberException.class, () -> service.create(request("dhaka metro ga-12-3456")));
		verify(vehicles, never()).saveAndFlush(any(Vehicle.class));
	}

	@Test
	void updateAllowsVehicleToKeepItsNormalizedNumber() {
		Vehicle vehicle = existingVehicle();
		when(vehicles.findByVehicleNumber("DHAKA METRO GA-12-3456")).thenReturn(Optional.of(vehicle));
		when(vehicles.saveAndFlush(vehicle)).thenReturn(vehicle);

		VehicleResponse response = service.update(" dhaka metro ga-12-3456 ", request("DHAKA METRO GA-12-3456"));

		assertEquals("DHAKA METRO GA-12-3456", response.vehicleNumber());
		verify(vehicles, never()).existsByVehicleNumberAndIdNot(any(), any());
	}

	@Test
	void updateRejectsNumberOwnedByAnotherVehicle() {
		Vehicle vehicle = existingVehicle();
		when(vehicles.findByVehicleNumber("DHAKA METRO GA-12-3456")).thenReturn(Optional.of(vehicle));
		when(vehicles.existsByVehicleNumberAndIdNot("DHAKA METRO BA-11-2233", 42L)).thenReturn(true);

		assertThrows(DuplicateVehicleNumberException.class,
				() -> service.update("DHAKA METRO GA-12-3456", request("dhaka metro ba-11-2233")));
		verify(vehicles, never()).saveAndFlush(any(Vehicle.class));
	}

	@Test
	void translatesUniqueConstraintRaceToDuplicateVehicleException() {
		when(vehicles.existsByVehicleNumber("DHAKA METRO GA-12-3456")).thenReturn(false);
		when(vehicles.saveAndFlush(any(Vehicle.class))).thenThrow(
				new DataIntegrityViolationException("database detail", new SQLException("duplicate", "23505")));

		assertThrows(DuplicateVehicleNumberException.class, () -> service.create(request("DHAKA METRO GA-12-3456")));
	}

	@Test
	void findUnknownVehicleRaisesNotFound() {
		when(vehicles.findByVehicleNumber("UNKNOWN")).thenReturn(Optional.empty());
		assertThrows(VehicleNotFoundException.class, () -> service.findByNumber(" unknown "));
	}

	private Vehicle existingVehicle() {
		Vehicle vehicle = new Vehicle("DHAKA METRO GA-12-3456", "Stark Logistics", "Sedan",
				"Toyota", "Corolla", VehicleStatus.ACTIVE);
		ReflectionTestUtils.setField(vehicle, "id", 42L);
		return vehicle;
	}

	private static VehicleRequest request(String vehicleNumber) {
		return new VehicleRequest(vehicleNumber, "Stark Logistics", "Sedan", "Toyota", "Corolla", VehicleStatus.ACTIVE);
	}
}
