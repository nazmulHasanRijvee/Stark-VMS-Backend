package com.stark.vms;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.stark.vms.auth.AppUserRepository;
import com.stark.vms.config.DemoDataSeeder;
import com.stark.vms.vehicle.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"app.seed.demo-data=true",
		"app.seed.demo-password=password123",
		"app.jwt.access-token-lifetime=PT60H",
		"app.cors.allowed-origins=http://localhost:3000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTests {

	private static final String API = "/api/v1";
	private static final String DEMO_VEHICLE = "DHAKA METRO GA-12-3456";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private VehicleRepository vehicles;
	@Autowired
	private AppUserRepository users;
	@Autowired
	private DemoDataSeeder demoDataSeeder;
	@Value("${app.jwt.secret}")
	private String testJwtSecret;

	@BeforeEach
	void resetSeedData() {
		vehicles.deleteAll();
		users.deleteAll();
		demoDataSeeder.run(new DefaultApplicationArguments(new String[0]));
	}

	@Test
	void publicRegistrationReturnsSafeUserAndDuplicateEmailConflict() throws Exception {
		String registration = """
				{"fullName":"Jane Doe","email":" Jane@Example.com ","password":"secret123"}
				""";
		mockMvc.perform(post(API + "/auth/register").contentType(MediaType.APPLICATION_JSON).content(registration))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.fullName").value("Jane Doe"))
				.andExpect(jsonPath("$.data.email").value("jane@example.com"))
				.andExpect(jsonPath("$.data.role").value("ROLE_USER"))
				.andExpect(jsonPath("$.data.accessToken").doesNotExist())
				.andExpect(jsonPath("$.data.passwordHash").doesNotExist());

		assertTrue(users.findByEmail("jane@example.com").orElseThrow().getPasswordHash().startsWith("$2"));
		mockMvc.perform(post(API + "/auth/register/").contentType(MediaType.APPLICATION_JSON)
					.content("{\"fullName\":\"Jane Smith\",\"email\":\"JANE@example.com\",\"password\":\"secret123\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Email already in use."))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void malformedEmailAndShortPasswordReturnFieldErrors() throws Exception {
		mockMvc.perform(post(API + "/auth/register").contentType(MediaType.APPLICATION_JSON)
					.content("{\"fullName\":\"Jane Doe\",\"email\":\"not-an-email\",\"password\":\"123\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.fieldErrors.email").exists())
				.andExpect(jsonPath("$.fieldErrors.password").exists())
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void demoLoginReturnsOnlyNestedAccessTokenAndInvalidLoginIsGeneric() throws Exception {
		MvcResult loginResult = mockMvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"admin@stark.com\",\"password\":\"password123\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.data.expiresIn").value(216000))
				.andExpect(jsonPath("$.data.user.email").value("admin@stark.com"))
				.andExpect(jsonPath("$.data.refreshToken").doesNotExist())
				.andReturn();
		String token = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.accessToken");
		assertTrue(token.split("\\.").length == 3);

		mockMvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"missing@stark.com\",\"password\":\"wrong-pass\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid email or password."));
	}

	@Test
	void dashboardAndVehicleRoutesRejectMissingInvalidAndExpiredTokens() throws Exception {
		mockMvc.perform(get(API + "/dashboard/stats"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.timestamp").exists());
		mockMvc.perform(get(API + "/vehicles").header("Authorization", "Bearer not-a-jwt"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
		mockMvc.perform(get(API + "/vehicles").header("Authorization", "Bearer " + expiredToken()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Authentication is required or the access token is invalid."));
	}

	@Test
	void authenticatedVehicleCrudUsesEncodedPathAndReadOnlyMetadata() throws Exception {
		String token = loginToken();
		String authorization = "Bearer " + token;
		String createBody = """
				{"id":999,"vehicleNumber":"  test vehicle-1 ","companyName":"Stark Logistics","vehicleType":"Truck","brand":"Ford","model":"Ranger","status":"Active","createdAt":"2000-01-01T00:00:00Z","updatedAt":"2000-01-01T00:00:00Z"}
				""";
		MvcResult created = mockMvc.perform(post(API + "/vehicles").header("Authorization", authorization)
					.contentType(MediaType.APPLICATION_JSON).content(createBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.id").isNumber())
				.andExpect(jsonPath("$.data.vehicleNumber").value("TEST VEHICLE-1"))
				.andExpect(jsonPath("$.data.status").value("Active"))
				.andExpect(jsonPath("$.data.createdAt").exists())
				.andExpect(jsonPath("$.data.updatedAt").exists())
				.andReturn();
		Number idValue = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");
		long id = idValue.longValue();
		assertNotEquals(999L, id);

		URI encodedPath = URI.create(API + "/vehicles/TEST%20VEHICLE-1");
		mockMvc.perform(get(encodedPath).header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.vehicleNumber").value("TEST VEHICLE-1"));

		String updateBody = """
				{"vehicleNumber":"test vehicle-1","companyName":"Stark Prime Fleet","vehicleType":"Truck","brand":"Ford","model":"Ranger X","status":"In Service"}
				""";
		mockMvc.perform(put(encodedPath).header("Authorization", authorization)
					.contentType(MediaType.APPLICATION_JSON).content(updateBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("In Service"))
				.andExpect(jsonPath("$.data.id").value(id));

		mockMvc.perform(delete(encodedPath).header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isMap())
				.andExpect(jsonPath("$.message").value("Vehicle deleted successfully"));
		mockMvc.perform(get(encodedPath).header("Authorization", authorization))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	void duplicateVehicleCreateAndUpdateReturnConflictWithoutOverwriting() throws Exception {
		String authorization = "Bearer " + loginToken();
		String duplicate = """
				{"vehicleNumber":" dhaka metro ga-12-3456 ","companyName":"Replacement","vehicleType":"Sedan","brand":"Other","model":"Other","status":"Active"}
				""";
		mockMvc.perform(post(API + "/vehicles").header("Authorization", authorization)
					.contentType(MediaType.APPLICATION_JSON).content(duplicate))
				.andExpect(status().isConflict());

		String renameToExisting = """
				{"vehicleNumber":"dhaka metro ba-11-2233","companyName":"Stark Logistics","vehicleType":"Sedan","brand":"Toyota","model":"Corolla","status":"Active"}
				""";
		URI demoVehiclePath = URI.create(API + "/vehicles/DHAKA%20METRO%20GA-12-3456");
		MvcResult renameResult = mockMvc.perform(put(demoVehiclePath)
					.header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(renameToExisting))
				.andReturn();
		assertEquals(409, renameResult.getResponse().getStatus(), renameResult.getResponse().getContentAsString());
		assertEquals(4L, vehicles.count());
		assertEquals("Stark Logistics", vehicles.findByVehicleNumber(DEMO_VEHICLE).orElseThrow().getCompanyName());
	}

	@Test
	void searchStatusPaginationAndEmptyPagesAreStable() throws Exception {
		String authorization = "Bearer " + loginToken();
		MvcResult first = mockMvc.perform(get(API + "/vehicles?search=dhaka&status=Active&page=0&size=1")
					.header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(1))
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.totalPages").value(2))
				.andExpect(jsonPath("$.data.content.length()").value(1))
				.andReturn();
		String firstVehicle = JsonPath.read(first.getResponse().getContentAsString(), "$.data.content[0].vehicleNumber");
		MvcResult repeat = mockMvc.perform(get(API + "/vehicles?search=dhaka&status=Active&page=0&size=1")
					.header("Authorization", authorization)).andReturn();
		assertEquals(firstVehicle, JsonPath.read(repeat.getResponse().getContentAsString(), "$.data.content[0].vehicleNumber"));

		mockMvc.perform(get(API + "/vehicles?page=5&size=10").header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(0))
				.andExpect(jsonPath("$.data.totalElements").value(4));
		mockMvc.perform(get(API + "/vehicles?status=active").header("Authorization", authorization))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false));
		mockMvc.perform(get(API + "/vehicles?size=101").header("Authorization", authorization))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Page size must be between 1 and 100."));
	}

	@Test
	void dashboardCountsReflectCreateUpdateAndDelete() throws Exception {
		String authorization = "Bearer " + loginToken();
		assertDashboardCounts(authorization, 4, 2, 1, 1);

		String vehicle = """
				{"vehicleNumber":"COUNT TEST-1","companyName":"Stark Lab","vehicleType":"Van","brand":"Volvo","model":"V60","status":"Active"}
				""";
		mockMvc.perform(post(API + "/vehicles").header("Authorization", authorization)
					.contentType(MediaType.APPLICATION_JSON).content(vehicle))
				.andExpect(status().isCreated());
		assertDashboardCounts(authorization, 5, 3, 1, 1);

		String update = """
				{"vehicleNumber":"COUNT TEST-1","companyName":"Stark Lab","vehicleType":"Van","brand":"Volvo","model":"V60","status":"Inactive"}
				""";
		URI countVehiclePath = URI.create(API + "/vehicles/COUNT%20TEST-1");
		MvcResult updateResult = mockMvc.perform(put(countVehiclePath).header("Authorization", authorization)
					.contentType(MediaType.APPLICATION_JSON).content(update))
				.andReturn();
		assertEquals(200, updateResult.getResponse().getStatus(), updateResult.getResponse().getContentAsString());
		assertDashboardCounts(authorization, 5, 2, 1, 2);

		mockMvc.perform(delete(countVehiclePath).header("Authorization", authorization))
				.andExpect(status().isOk());
		assertDashboardCounts(authorization, 4, 2, 1, 1);
	}

	@Test
	void corsPreflightAllowsConfiguredOriginAndRejectsOtherOrigins() throws Exception {
		mockMvc.perform(options(API + "/vehicles")
					.header("Origin", "http://localhost:3000")
					.header("Access-Control-Request-Method", "GET")
					.header("Access-Control-Request-Headers", "authorization,content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));

		mockMvc.perform(options(API + "/vehicles")
					.header("Origin", "https://not-configured.example")
					.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	private void assertDashboardCounts(String authorization, int total, int active, int inService, int inactive)
			throws Exception {
		mockMvc.perform(get(API + "/dashboard/stats").header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalVehicles").value(total))
				.andExpect(jsonPath("$.data.activeVehicles").value(active))
				.andExpect(jsonPath("$.data.inServiceVehicles").value(inService))
				.andExpect(jsonPath("$.data.inactiveVehicles").value(inactive));
	}

	private String loginToken() throws Exception {
		MvcResult result = mockMvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"admin@stark.com\",\"password\":\"password123\"}"))
				.andExpect(status().isOk()).andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
	}

	private String expiredToken() throws Exception {
		Instant now = Instant.now();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.subject("1").claim("email", "admin@stark.com").claim("role", "ROLE_ADMIN")
				.issueTime(Date.from(now.minusSeconds(120)))
				.expirationTime(Date.from(now.minusSeconds(60))).build();
		SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
		token.sign(new MACSigner(Base64.getDecoder().decode(testJwtSecret)));
		return token.serialize();
	}
}
