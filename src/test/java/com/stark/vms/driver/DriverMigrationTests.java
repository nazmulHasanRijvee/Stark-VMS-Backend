package com.stark.vms.driver;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DriverMigrationTests {
	@Test
	void v4PreservesExistingVehiclesDocumentsAndMaintenanceAndEnforcesSingleAssignment() {
		SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
				"jdbc:h2:mem:driver_migration_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1",
				"sa", "", true);
		try {
			new ResourceDatabasePopulator(
					new ClassPathResource("db/migration/V1__create_users_and_vehicles.sql"),
					new ClassPathResource("db/migration/V2__constrain_user_roles.sql"),
					new ClassPathResource("db/migration/V3__assignment_2_documents_maintenance_and_status.sql"))
					.execute(dataSource);
			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			jdbc.update("INSERT INTO vehicles (vehicle_number, company_name, vehicle_type, brand, model, status, created_at, updated_at) "
					+ "VALUES ('TEST-OLD', 'Stark Fleet', 'Van', 'Toyota', 'Hiace', 'Maintenance', "
					+ "TIMESTAMP WITH TIME ZONE '2024-01-02 03:04:05+00:00', TIMESTAMP WITH TIME ZONE '2024-02-03 04:05:06+00:00')");
			Long vehicleId = jdbc.queryForObject("SELECT id FROM vehicles WHERE vehicle_number = 'TEST-OLD'", Long.class);
			jdbc.update("INSERT INTO vehicle_documents (vehicle_id, document_type, display_filename, storage_object_key, content_type, file_size_bytes, expiry_date, uploaded_at) "
					+ "VALUES (?, 'Registration', 'registration.pdf', 'vehicles/test/registration.pdf', 'application/pdf', 32, DATE '2027-08-01', "
					+ "TIMESTAMP WITH TIME ZONE '2024-03-04 05:06:07+00:00')", vehicleId);
			jdbc.update("INSERT INTO vehicle_maintenance_records (vehicle_id, maintenance_date, maintenance_type, cost, workshop, notes, created_at) "
					+ "VALUES (?, DATE '2024-04-05', 'Oil change', 4500.00, 'Stark Workshop', 'Existing record', "
					+ "TIMESTAMP WITH TIME ZONE '2024-04-05 06:07:08+00:00')", vehicleId);

			new ResourceDatabasePopulator(new ClassPathResource(
					"db/migration/V4__drivers_and_current_vehicle_assignment.sql")).execute(dataSource);

			assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM vehicles", Integer.class));
			assertEquals("Maintenance", jdbc.queryForObject("SELECT status FROM vehicles WHERE id = ?", String.class, vehicleId));
			assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM vehicle_documents WHERE vehicle_id = ?", Integer.class, vehicleId));
			assertEquals("vehicles/test/registration.pdf", jdbc.queryForObject(
					"SELECT storage_object_key FROM vehicle_documents WHERE vehicle_id = ?", String.class, vehicleId));
			assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM vehicle_maintenance_records WHERE vehicle_id = ?", Integer.class, vehicleId));
			assertEquals("Existing record", jdbc.queryForObject(
					"SELECT notes FROM vehicle_maintenance_records WHERE vehicle_id = ?", String.class, vehicleId));
			assertEquals(null, jdbc.queryForObject("SELECT current_driver_id FROM vehicles WHERE id = ?", Long.class, vehicleId));

			jdbc.update("INSERT INTO drivers (name, phone, license_number, license_expiry, status, created_at, updated_at) "
					+ "VALUES ('Alex Driver', '+8801700123456', 'AB-1234', DATE '2028-07-31', 'Active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
			Long driverId = jdbc.queryForObject("SELECT id FROM drivers WHERE license_number = 'AB-1234'", Long.class);
			assertThrows(DataIntegrityViolationException.class,
					() -> jdbc.update("INSERT INTO drivers (name, phone, license_number, license_expiry, status, created_at, updated_at) "
							+ "VALUES ('Duplicate', '123', 'AB-1234', DATE '2028-07-31', 'Active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"));
			jdbc.update("UPDATE vehicles SET current_driver_id = ? WHERE id = ?", driverId, vehicleId);
			jdbc.update("INSERT INTO vehicles (vehicle_number, company_name, vehicle_type, brand, model, status, created_at, updated_at) "
					+ "VALUES ('TEST-SECOND', 'Stark Fleet', 'Van', 'Toyota', 'Hiace', 'Active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
			Long secondVehicleId = jdbc.queryForObject("SELECT id FROM vehicles WHERE vehicle_number = 'TEST-SECOND'", Long.class);
			assertThrows(DataIntegrityViolationException.class,
					() -> jdbc.update("UPDATE vehicles SET current_driver_id = ? WHERE id = ?", driverId, secondVehicleId));
			assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM vehicles WHERE current_driver_id = ?", Integer.class, driverId));
		} finally {
			dataSource.destroy();
		}
	}
}
