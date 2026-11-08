package com.stark.vms.auth;

import com.stark.vms.common.error.DomainValidationException;
import com.stark.vms.common.error.DuplicateEmailException;
import com.stark.vms.common.error.InvalidCredentialsException;
import com.stark.vms.common.error.UniqueConstraintViolation;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AuthService {

	private static final Pattern EMAIL_FORMAT = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
	private final AppUserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenService tokens;

	public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtTokenService tokens) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tokens = tokens;
	}

	@Transactional
	public UserResponse register(RegisterRequest request) {
		if (request == null) {
			throw new DomainValidationException("Registration details are required.");
		}
		String fullName = request.fullName() == null ? "" : request.fullName().strip();
		String email = normalizeEmail(request.email());
		String password = request.password();
		if (fullName.length() < 2 || fullName.length() > 100) {
			throw new DomainValidationException("Full name must contain between 2 and 100 characters.");
		}
		if (email.length() > 254 || !EMAIL_FORMAT.matcher(email).matches()) {
			throw new DomainValidationException("A valid email address is required.");
		}
		if (password == null || password.length() < 6
				|| password.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new DomainValidationException("Password must contain at least 6 characters and be at most 72 UTF-8 bytes.");
		}
		if (users.existsByEmail(email)) {
			throw new DuplicateEmailException();
		}

		AppUser user = new AppUser(fullName, email, passwordEncoder.encode(password), AppUser.ROLE_USER);
		try {
			return UserResponse.from(users.saveAndFlush(user));
		} catch (DataIntegrityViolationException exception) {
			if (UniqueConstraintViolation.isUniqueConstraintViolation(exception)) {
				throw new DuplicateEmailException();
			}
			throw exception;
		}
	}

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		if (request == null || request.username() == null || request.username().isBlank()
				|| request.password() == null
				|| request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new InvalidCredentialsException();
		}
		String email = normalizeEmail(request.username());
		AppUser user = users.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}
		IssuedAccessToken issued = tokens.issue(user);
		return new LoginResponse(issued.value(), "Bearer", tokens.getAccessTokenLifetimeSeconds(),
				UserResponse.from(user));
	}

	public static String normalizeEmail(String email) {
		return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
	}
}
