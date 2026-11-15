package com.stark.vms;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.stark.vms.auth.AppUserRepository;
import com.stark.vms.config.DemoDataSeeder;
import com.stark.vms.documents.DocumentStorage;
import com.stark.vms.documents.VehicleDocumentRepository;
import com.stark.vms.maintenance.VehicleMaintenanceRepository;
import com.stark.vms.vehicle.Vehicle;
import com.stark.vms.vehicle.VehicleRepository;
import com.stark.vms.vehicle.VehicleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;

import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

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
@Import(ApiIntegrationTests.TestStorageConfiguration.class)
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
	private VehicleDocumentRepository documents;
	@Autowired
	private VehicleMaintenanceRepository maintenance;
	@Autowired
	private RecordingDocumentStorage storage;
	@Autowired
	private DemoDataSeeder demoDataSeeder;
	@Value("${app.jwt.secret}")
	private String testJwtSecret;

	@BeforeEach
	void resetSeedData() {
		documents.deleteAllInBatch();
		maintenance.deleteAllInBatch();
		vehicles.deleteAll();
		users.deleteAll();
		storage.clear();
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
				{"vehicleNumber":"test vehicle-1","companyName":"Stark Prime Fleet","vehicleType":"Truck","brand":"Ford","model":"Ranger X","status":"Maintenance"}
				""";
		mockMvc.perform(put(encodedPath).header("Authorization", authorization)
					.contentType(MediaType.APPLICATION_JSON).content(updateBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("Maintenance"))
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
		assertDashboardCounts(authorization, 5, 2, 2, 1);

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

	private void assertDashboardCounts(String authorization, int total, int active, int inactive, int maintenanceCount)
			throws Exception {
		mockMvc.perform(get(API + "/dashboard/stats").header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalVehicles").value(total))
				.andExpect(jsonPath("$.data.activeVehicles").value(active))
				.andExpect(jsonPath("$.data.inactiveVehicles").value(inactive))
				.andExpect(jsonPath("$.data.maintenanceVehicles").value(maintenanceCount))
				.andExpect(jsonPath("$.data.inServiceVehicles").doesNotExist())
				.andExpect(jsonPath("$.data.expiredDocuments").value(0))
				.andExpect(jsonPath("$.data.expiringSoonDocuments").value(0));
	}

	@Test
	void statusSearchAndAllowListedSortingUseAssignmentTwoContract() throws Exception {
		String authorization = "Bearer " + loginToken();
		mockMvc.perform(get(API + "/vehicles?status=In%20Service").header("Authorization", authorization))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get(API + "/vehicles?search=van").header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].vehicleNumber").value("DHAKA METRO CHA-55-9812"));
		mockMvc.perform(get(API + "/vehicles?sortBy=vehicleNumber&sortDirection=desc&page=0&size=10")
					.header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].vehicleNumber").value("DHAKA METRO GA-12-3456"));
		mockMvc.perform(get(API + "/vehicles?sortBy=id%20desc").header("Authorization", authorization))
				.andExpect(status().isBadRequest());
	}

	@Test
	void searchRanksExactVehicleNumberThenPrefixThenOtherFields() throws Exception {
		String authorization = "Bearer " + loginToken();
		vehicles.saveAll(List.of(
				new Vehicle("DHAKA METRO GA-12-3456-ALT", "Fleet A", "Sedan", "Brand A", "Model A", VehicleStatus.ACTIVE),
				new Vehicle("X-DHAKA METRO GA-12-3456", "Fleet B", "Sedan", "Brand B", "Model B", VehicleStatus.ACTIVE),
				new Vehicle("OTHER-1", "DHAKA METRO GA-12-3456", "Sedan", "Brand C", "Model C", VehicleStatus.ACTIVE)));
		mockMvc.perform(get(API + "/vehicles").param("search", "DHAKA METRO GA-12-3456")
					.param("page", "0").param("size", "10")
					.header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].vehicleNumber").value("DHAKA METRO GA-12-3456"))
				.andExpect(jsonPath("$.data.content[1].vehicleNumber").value("DHAKA METRO GA-12-3456-ALT"))
				.andExpect(jsonPath("$.data.content[2].vehicleNumber").value("X-DHAKA METRO GA-12-3456"))
				.andExpect(jsonPath("$.data.content[3].vehicleNumber").value("OTHER-1"));
	}

	@Test
	void maintenanceHistoryIsDatabaseBackedAndDoesNotChangeVehicleStatus() throws Exception {
		String authorization = "Bearer " + loginToken();
		String beforeStatus = vehicles.findByVehicleNumber(DEMO_VEHICLE).orElseThrow().getStatus().getDisplayValue();
		String today = LocalDate.now(ZoneId.of("Asia/Dhaka")).toString();
		String body = """
				{"maintenanceDate":"%s","maintenanceType":"Oil and filter change","cost":4500.50,"workshop":"Stark Service Center","notes":"Routine service"}
				""".formatted(today);
		URI vehicleMaintenance = URI.create(API + "/vehicles/DHAKA%20METRO%20GA-12-3456/maintenance");
		mockMvc.perform(post(vehicleMaintenance).header("Authorization", authorization)
					.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.cost").value(4500.50))
				.andExpect(jsonPath("$.data.workshop").value("Stark Service Center"));
		mockMvc.perform(get(vehicleMaintenance).header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].maintenanceType").value("Oil and filter change"));
		mockMvc.perform(get(API + "/dashboard/stats").header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.maintenanceRecordsThisMonth").value(1))
				.andExpect(jsonPath("$.data.maintenanceCostThisMonth").value(4500.50))
				.andExpect(jsonPath("$.data.recentMaintenance[0].workshop").value("Stark Service Center"));
		assertEquals(beforeStatus, vehicles.findByVehicleNumber(DEMO_VEHICLE).orElseThrow().getStatus().getDisplayValue());
	}

	@Test
	void documentUploadHistoryExpiryAndDownloadAreVehicleScoped() throws Exception {
		String authorization = "Bearer " + loginToken();
		byte[] pdf = "%PDF-1.7\nVehicle document".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		URI documentPath = URI.create(API + "/vehicles/DHAKA%20METRO%20GA-12-3456/documents");
		MockMultipartFile firstFile = new MockMultipartFile("file", "../registration.pdf", "text/plain", pdf);
		MvcResult first = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(documentPath)
					.file(firstFile).param("documentType", "Registration")
					.param("expiryDate", LocalDate.now(ZoneId.of("Asia/Dhaka")).plusDays(30).toString())
					.header("Authorization", authorization))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.filename").value("registration.pdf"))
				.andExpect(jsonPath("$.data.contentType").value("application/pdf"))
				.andExpect(jsonPath("$.data.expiryState").value("EXPIRING_SOON"))
				.andExpect(jsonPath("$.data.latest").value(true))
				.andReturn();
		long documentId = ((Number) JsonPath.read(first.getResponse().getContentAsString(), "$.data.id")).longValue();
		mockMvc.perform(get(API + "/dashboard/stats").header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.expiringSoonDocuments").value(1))
				.andExpect(jsonPath("$.data.expiringSoon[0].documentId").value(documentId));
		MockMultipartFile secondFile = new MockMultipartFile("file", "registration-renewed.pdf", "application/pdf", pdf);
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(documentPath)
					.file(secondFile).param("documentType", "Registration")
					.header("Authorization", authorization))
				.andExpect(status().isCreated());
		mockMvc.perform(get(documentPath).header("Authorization", authorization).param("documentType", "Registration"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.content[0].latest").value(true))
				.andExpect(jsonPath("$.data.content[1].latest").value(false));
		mockMvc.perform(get(API + "/dashboard/stats").header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.expiringSoonDocuments").value(0));
		mockMvc.perform(get(URI.create(documentPath + "/" + documentId + "/download"))
					.header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.startsWith("https://signed.test/")))
				.andExpect(jsonPath("$.data.expiresInSeconds").value(300));
		URI otherVehicleDocument = URI.create(API + "/vehicles/CHATTOGRAM%20METRO%20KHA-22-1100/documents/" + documentId + "/download");
		mockMvc.perform(get(otherVehicleDocument).header("Authorization", authorization))
				.andExpect(status().isNotFound());
		MockMultipartFile invalid = new MockMultipartFile("file", "fake.pdf", "application/pdf", "not pdf".getBytes());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(documentPath)
					.file(invalid).param("documentType", "Registration").header("Authorization", authorization))
				.andExpect(status().isBadRequest());
		URI vehiclePath = URI.create(API + "/vehicles/DHAKA%20METRO%20GA-12-3456");
		MvcResult deletion = mockMvc.perform(delete(vehiclePath).header("Authorization", authorization)).andReturn();
		assertEquals(200, deletion.getResponse().getStatus(), deletion.getResponse().getContentAsString());
		assertEquals("Vehicle deleted successfully", JsonPath.read(deletion.getResponse().getContentAsString(), "$.message"));
		assertEquals(0, documents.count());
		assertEquals(0, storage.size());
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

	@TestConfiguration
	static class TestStorageConfiguration {
		@Bean
		@Primary
		RecordingDocumentStorage recordingDocumentStorage() {
			return new RecordingDocumentStorage();
		}
	}

	static class RecordingDocumentStorage implements DocumentStorage {
		private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
		@Override public void upload(String key, byte[] contents, String contentType) { objects.put(key, contents.clone()); }
		@Override public void deleteObjects(List<String> keys) { keys.forEach(objects::remove); }
		@Override public String createSignedDownloadUrl(String key, int expiresInSeconds) {
			if (!objects.containsKey(key)) throw new IllegalStateException("missing test object");
			return "https://signed.test/" + key;
		}
		void clear() { objects.clear(); }
		int size() { return objects.size(); }
	}
}
