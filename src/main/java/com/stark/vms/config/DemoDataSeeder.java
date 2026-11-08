package com.stark.vms.config;

import com.stark.vms.auth.AppUser;
import com.stark.vms.auth.AppUserRepository;
import com.stark.vms.auth.AuthService;
import com.stark.vms.vehicle.Vehicle;
import com.stark.vms.vehicle.VehicleRepository;
import com.stark.vms.vehicle.VehicleService;
import com.stark.vms.vehicle.VehicleStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.nio.charset.StandardCharsets;

@Component
@Profile("!production & !prod")
@ConditionalOnProperty(name = "app.seed.demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

	private static final String DEMO_EMAIL = "admin@stark.com";
	private final AppUserRepository users;
	private final VehicleRepository vehicles;
	private final PasswordEncoder passwordEncoder;
	private final String demoPassword;

	public DemoDataSeeder(AppUserRepository users, VehicleRepository vehicles,
			PasswordEncoder passwordEncoder,
			@Value("${app.seed.demo-password:}") String demoPassword) {
		this.users = users;
		this.vehicles = vehicles;
		this.passwordEncoder = passwordEncoder;
		this.demoPassword = demoPassword;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (demoPassword == null || demoPassword.length() < 6
				|| demoPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new IllegalStateException("Set VMS_DEMO_PASSWORD to a value with at least 6 characters and at most 72 UTF-8 bytes.");
		}
		String normalizedEmail = AuthService.normalizeEmail(DEMO_EMAIL);
		if (!users.existsByEmail(normalizedEmail)) {
			users.saveAndFlush(new AppUser("John Doe", normalizedEmail,
					passwordEncoder.encode(demoPassword), AppUser.ROLE_ADMIN));
		}

		for (SeedVehicle seed : seedVehicles()) {
			String normalizedNumber = VehicleService.normalizeVehicleNumber(seed.vehicleNumber());
			if (!vehicles.existsByVehicleNumber(normalizedNumber)) {
				vehicles.saveAndFlush(new Vehicle(normalizedNumber, seed.companyName(), seed.vehicleType(),
						seed.brand(), seed.model(), seed.status()));
			}
		}
	}

	private static List<SeedVehicle> seedVehicles() {
		return List.of(
				new SeedVehicle("DHAKA METRO GA-12-3456", "Stark Logistics", "Sedan", "Toyota", "Corolla", VehicleStatus.ACTIVE),
				new SeedVehicle("DHAKA METRO CHA-55-9812", "Stark Agro", "Microbus / Van", "Nissan", "Urvan", VehicleStatus.IN_SERVICE),
				new SeedVehicle("DHAKA METRO BA-11-2233", "Stark Retail", "Sedan", "Honda", "Civic", VehicleStatus.ACTIVE),
				new SeedVehicle("CHATTOGRAM METRO KHA-22-1100", "Stark Tech", "SUV", "Mitsubishi", "Pajero", VehicleStatus.INACTIVE)
		);
	}

	private record SeedVehicle(String vehicleNumber, String companyName, String vehicleType,
			String brand, String model, VehicleStatus status) {
	}
}
