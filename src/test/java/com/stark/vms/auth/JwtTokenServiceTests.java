package com.stark.vms.auth;

import com.stark.vms.common.error.InvalidAccessTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenServiceTests {

	private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");
	private static final byte[] SECRET_BYTES = "test-secret-material-32-bytes-long!".getBytes(java.nio.charset.StandardCharsets.UTF_8);
	private static final String SECRET = Base64.getEncoder().encodeToString(SECRET_BYTES);

	@Test
	void issuedTokenHasExpectedClaimsAndConfiguredExpiry() {
		JwtTokenService service = service(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(24));
		AppUser user = user();

		IssuedAccessToken issued = service.issue(user);
		ParsedAccessToken parsed = service.parseAndValidate(issued.value());

		assertEquals(42L, parsed.userId());
		assertEquals("jane@stark.com", parsed.email());
		assertEquals(AppUser.ROLE_USER, parsed.role());
		assertEquals(NOW, parsed.issuedAt());
		assertEquals(NOW.plus(Duration.ofHours(24)), parsed.expiresAt());
		assertEquals(parsed.expiresAt(), issued.expiresAt());
		assertEquals(86400L, service.getAccessTokenLifetimeSeconds());
		assertFalse(issued.value().contains(SECRET));
	}

	@Test
	void tokenIsRejectedAtExpirationTime() {
		JwtTokenService issuer = service(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(1));
		String token = issuer.issue(user()).value();
		JwtTokenService validator = service(Clock.fixed(NOW.plus(Duration.ofHours(1)), ZoneOffset.UTC), Duration.ofHours(1));

		assertThrows(InvalidAccessTokenException.class, () -> validator.parseAndValidate(token));
	}

	@Test
	void tokenSignedWithAnotherSecretIsRejected() {
		String token = service(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(1)).issue(user()).value();
		String otherSecret = Base64.getEncoder().encodeToString(new byte[32]);
		JwtTokenService validator = new JwtTokenService(otherSecret, Duration.ofHours(1), Clock.fixed(NOW, ZoneOffset.UTC));

		assertThrows(InvalidAccessTokenException.class, () -> validator.parseAndValidate(token));
	}

	@Test
	void rejectsMissingOrWeakSigningSecretAtConstruction() {
		assertThrows(IllegalStateException.class,
				() -> new JwtTokenService("", Duration.ofHours(1), Clock.systemUTC()));
		assertThrows(IllegalStateException.class,
				() -> new JwtTokenService(Base64.getEncoder().encodeToString(new byte[16]), Duration.ofHours(1), Clock.systemUTC()));
	}

	private static JwtTokenService service(Clock clock, Duration lifetime) {
		return new JwtTokenService(SECRET, lifetime, clock);
	}

	private static AppUser user() {
		AppUser user = new AppUser("Jane Doe", "jane@stark.com", "hash", AppUser.ROLE_USER);
		ReflectionTestUtils.setField(user, "id", 42L);
		return user;
	}
}
