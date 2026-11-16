package com.stark.vms.vehicle;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VehicleStatusMigrationTests {
	@Test
	void v3ConvertsOnlyRetiredStatusAndPreservesExistingVehicleData() {
		SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
				"jdbc:h2:mem:status_migration_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1",
				"sa", "", true);
		try {
			new ResourceDatabasePopulator(
					new ClassPathResource("db/migration/V1__create_users_and_vehicles.sql"),
					new ClassPathResource("db/migration/V2__constrain_user_roles.sql")).execute(dataSource);
			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			jdbc.update("INSERT INTO vehicles (vehicle_number, company_name, vehicle_type, brand, model, status, created_at, updated_at) "
					+ "VALUES ('TEST-OLD', 'Stark Fleet', 'Van', 'Toyota', 'Hiace', 'In Service', "
					+ "TIMESTAMP WITH TIME ZONE '2024-01-02 03:04:05+00:00', TIMESTAMP WITH TIME ZONE '2024-02-03 04:05:06+00:00')");
			Map<String, Object> before = jdbc.queryForMap("SELECT vehicle_number, company_name, vehicle_type, brand, model, created_at, updated_at "
					+ "FROM vehicles WHERE vehicle_number = 'TEST-OLD'");

			new ResourceDatabasePopulator(new ClassPathResource(
					"db/migration/V3__assignment_2_documents_maintenance_and_status.sql")).execute(dataSource);

			Map<String, Object> after = jdbc.queryForMap("SELECT vehicle_number, company_name, vehicle_type, brand, model, status, created_at, updated_at "
					+ "FROM vehicles WHERE vehicle_number = 'TEST-OLD'");
			assertEquals("Maintenance", after.get("STATUS"));
			for (String field : new String[] {"VEHICLE_NUMBER", "COMPANY_NAME", "VEHICLE_TYPE", "BRAND", "MODEL", "CREATED_AT", "UPDATED_AT"}) {
				assertEquals(before.get(field), after.get(field), field + " should be preserved by the status migration");
			}
		} finally {
			dataSource.destroy();
		}
	}
}
