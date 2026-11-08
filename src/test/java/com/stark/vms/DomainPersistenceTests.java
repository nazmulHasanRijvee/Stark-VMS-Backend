package com.stark.vms;

import com.stark.vms.auth.AppUser;
import com.stark.vms.auth.AppUserRepository;
import com.stark.vms.config.DemoDataSeeder;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class DomainPersistenceTests {

	@Autowired
	private AppUserRepository users;
	@Autowired
	private VehicleRepository vehicles;
	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void clearDomainRows() {
		vehicles.deleteAll();
		users.deleteAll();
	}

	@Test
	void flywaySchemaStoresGeneratedIdsAndVehicleStatusDisplayValues() {
		AppUser savedUser = users.saveAndFlush(new AppUser("Jane Doe", "jane@stark.com", "bcrypt-hash", AppUser.ROLE_USER));
		Vehicle savedVehicle = vehicles.saveAndFlush(new Vehicle("DHAKA METRO GA-12-3456", "Stark Logistics",
				"Sedan", "Toyota", "Corolla", VehicleStatus.IN_SERVICE));

		assertNotNull(savedUser.getId());
		assertNotNull(savedUser.getCreatedAt());
		assertNotNull(savedVehicle.getId());
		assertEquals(VehicleStatus.IN_SERVICE,
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
