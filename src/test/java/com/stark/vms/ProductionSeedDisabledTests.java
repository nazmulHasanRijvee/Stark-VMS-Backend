package com.stark.vms;

import com.stark.vms.config.DemoDataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "app.seed.demo-data=true")
@ActiveProfiles({"test", "production"})
class ProductionSeedDisabledTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void demoSeederIsNotRegisteredInProductionProfile() {
		assertTrue(applicationContext.getBeansOfType(DemoDataSeeder.class).isEmpty());
	}
}
