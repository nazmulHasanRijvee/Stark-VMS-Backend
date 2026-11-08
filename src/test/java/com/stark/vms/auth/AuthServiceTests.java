package com.stark.vms.auth;

import com.stark.vms.common.error.DuplicateEmailException;
import com.stark.vms.common.error.InvalidCredentialsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

	@Mock
	private AppUserRepository users;
	@Mock
	private JwtTokenService tokens;

	private BCryptPasswordEncoder passwordEncoder;
	private AuthService service;

	@BeforeEach
	void setUp() {
		passwordEncoder = new BCryptPasswordEncoder(4);
		service = new AuthService(users, passwordEncoder, tokens);
	}

	@Test
	void registrationNormalizesEmailTrimsNameAndStoresOnlyBcryptHash() {
		when(users.existsByEmail("jane@stark.com")).thenReturn(false);
		when(users.saveAndFlush(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UserResponse response = service.register(new RegisterRequest(" Jane Doe ", " JANE@Stark.com ", "secure123"));
		ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);

		assertEquals("Jane Doe", response.fullName());
		assertEquals("jane@stark.com", response.email());
		assertEquals(AppUser.ROLE_USER, response.role());
		verify(users).saveAndFlush(captor.capture());
		AppUser saved = captor.getValue();
		assertNotEquals("secure123", saved.getPasswordHash());
		assertTrue(saved.getPasswordHash().startsWith("$2"));
		assertTrue(passwordEncoder.matches("secure123", saved.getPasswordHash()));
		assertEquals("jane@stark.com", saved.getEmail());
		assertFalse(response.toString().contains("secure123"));
	}

	@Test
	void registrationRejectsExistingNormalizedEmail() {
		when(users.existsByEmail("jane@stark.com")).thenReturn(true);
		assertThrows(DuplicateEmailException.class,
				() -> service.register(new RegisterRequest("Jane Doe", " JANE@STARK.COM ", "secure123")));
		verify(users, never()).saveAndFlush(any(AppUser.class));
	}

	@Test
	void translatesDuplicateEmailRace() {
		when(users.existsByEmail("jane@stark.com")).thenReturn(false);
		when(users.saveAndFlush(any(AppUser.class))).thenThrow(
				new DataIntegrityViolationException("database detail", new SQLException("duplicate", "23505")));
		assertThrows(DuplicateEmailException.class,
				() -> service.register(new RegisterRequest("Jane Doe", "jane@stark.com", "secure123")));
	}

	@Test
	void loginReturnsOneAccessTokenForValidCredentials() {
		AppUser user = new AppUser("Jane Doe", "jane@stark.com", passwordEncoder.encode("secure123"), AppUser.ROLE_USER);
		when(users.findByEmail("jane@stark.com")).thenReturn(Optional.of(user));
		when(tokens.issue(user)).thenReturn(new IssuedAccessToken("signed.jwt.value", Instant.parse("2026-11-09T00:00:00Z")));
		when(tokens.getAccessTokenLifetimeSeconds()).thenReturn(86400L);

		LoginResponse result = service.login(new LoginRequest(" JANE@STARK.COM ", "secure123"));

		assertEquals("signed.jwt.value", result.accessToken());
		assertEquals("Bearer", result.tokenType());
		assertEquals(86400L, result.expiresIn());
		assertEquals("jane@stark.com", result.user().email());
		assertFalse(result.toString().contains(user.getPasswordHash()));
	}

	@Test
	void unknownEmailAndWrongPasswordUseSameGenericFailure() {
		when(users.findByEmail("missing@stark.com")).thenReturn(Optional.empty());
		InvalidCredentialsException unknown = assertThrows(InvalidCredentialsException.class,
				() -> service.login(new LoginRequest("missing@stark.com", "wrong123")));

		AppUser user = new AppUser("Jane Doe", "jane@stark.com", passwordEncoder.encode("secure123"), AppUser.ROLE_USER);
		when(users.findByEmail("jane@stark.com")).thenReturn(Optional.of(user));
		InvalidCredentialsException wrongPassword = assertThrows(InvalidCredentialsException.class,
				() -> service.login(new LoginRequest("jane@stark.com", "wrong123")));

		assertEquals(unknown.getMessage(), wrongPassword.getMessage());
	}
}
