package com.stark.vms;

import com.stark.vms.auth.AppUser;
import com.stark.vms.auth.AppUserRepository;
import com.stark.vms.config.DemoDataSeeder;
import com.stark.vms.documents.VehicleDocumentRepository;
import com.stark.vms.maintenance.VehicleMaintenanceRepository;
import com.stark.vms.vehicle.Vehicle;
import com.stark.vms.vehicle.VehicleRepository;
import com.stark.vms.vehicle.VehicleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class DomainPersistenceTests {

	@Autowired
	private AppUserRepository users;
	@Autowired
	private VehicleRepository vehicles;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private VehicleDocumentRepository documents;
	@Autowired
	private VehicleMaintenanceRepository maintenance;

	@BeforeEach
	void clearDomainRows() {
		documents.deleteAllInBatch();
		maintenance.deleteAllInBatch();
		vehicles.deleteAll();
		users.deleteAll();
	}

	@Test
	void migratedStatusConstraintRejectsTheRetiredInServiceValue() {
		Vehicle savedVehicle = vehicles.saveAndFlush(new Vehicle("DHAKA METRO TEST-1", "Stark Logistics",
				"Sedan", "Toyota", "Corolla", VehicleStatus.ACTIVE));
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("UPDATE vehicles SET status = 'In Service' WHERE id = ?", savedVehicle.getId()));
	}

	@Test
	void flywaySchemaStoresGeneratedIdsAndVehicleStatusDisplayValues() {
		AppUser savedUser = users.saveAndFlush(new AppUser("Jane Doe", "jane@stark.com", "bcrypt-hash", AppUser.ROLE_USER));
		Vehicle savedVehicle = vehicles.saveAndFlush(new Vehicle("DHAKA METRO GA-12-3456", "Stark Logistics",
				"Sedan", "Toyota", "Corolla", VehicleStatus.MAINTENANCE));

		assertNotNull(savedUser.getId());
		assertNotNull(savedUser.getCreatedAt());
		assertNotNull(savedVehicle.getId());
		assertEquals(VehicleStatus.MAINTENANCE,
				vehicles.findByVehicleNumber("DHAKA METRO GA-12-3456").orElseThrow().getStatus());
		assertNotNull(savedVehicle.getCreatedAt());
		assertNotNull(savedVehicle.getUpdatedAt());
	}

	@Test
	void demoSeedingIsIdempotentAndDoesNotResetExistingDemoPassword() {
		String existingHash = passwordEncoder.encode("prior-password");
		users.saveAndFlush(new AppUser("Existing Admin", "admin@stark.com", existingHash, AppUser.ROLE_ADMIN));
		DemoDataSeeder seeder = new DemoDataSeeder(users, vehicles, passwordEncoder, "demo-password");
		DefaultApplicationArguments args = new DefaultApplicationArguments(new String[0]);

		seeder.run(args);
		seeder.run(args);

		assertEquals(1, users.count());
		assertEquals(4, vehicles.count());
		AppUser seededAdmin = users.findByEmail("admin@stark.com").orElseThrow();
		assertEquals("Existing Admin", seededAdmin.getFullName());
		assertTrue(passwordEncoder.matches("prior-password", seededAdmin.getPasswordHash()));
	}
}
