package com.stark.vms.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.stark.vms.common.error.InvalidAccessTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtTokenService {

	private final byte[] signingKey;
	private final Duration accessTokenLifetime;
	private final Clock clock;

	public JwtTokenService(
			@Value("${app.jwt.secret:}") String base64Secret,
			@Value("${app.jwt.access-token-lifetime:PT24H}") Duration accessTokenLifetime,
			Clock clock) {
		try {
			this.signingKey = Base64.getDecoder().decode(base64Secret);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("VMS_JWT_SECRET must be Base64-encoded random key material.");
		}
		if (signingKey.length < 32) {
			throw new IllegalStateException("VMS_JWT_SECRET must decode to at least 32 bytes.");
		}
		if (accessTokenLifetime == null || accessTokenLifetime.isZero() || accessTokenLifetime.isNegative()) {
			throw new IllegalStateException("JWT access-token lifetime must be a positive duration.");
		}
		this.accessTokenLifetime = accessTokenLifetime;
		this.clock = clock;
	}

	public IssuedAccessToken issue(AppUser user) {
		Instant issuedAt = clock.instant();
		Instant expiresAt = issuedAt.plus(accessTokenLifetime);
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.subject(user.getId().toString())
				.claim("email", user.getEmail())
				.claim("role", user.getRole())
				.issueTime(Date.from(issuedAt))
				.expirationTime(Date.from(expiresAt))
				.build();
		try {
			SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
			token.sign(new MACSigner(signingKey));
			return new IssuedAccessToken(token.serialize(), expiresAt);
		} catch (JOSEException exception) {
			throw new IllegalStateException("Unable to issue access token.", exception);
		}
	}

	public ParsedAccessToken parseAndValidate(String tokenValue) {
		if (tokenValue == null || tokenValue.isBlank()) {
			throw new InvalidAccessTokenException();
		}
		try {
			SignedJWT token = SignedJWT.parse(tokenValue);
			if (!JWSAlgorithm.HS256.equals(token.getHeader().getAlgorithm())
					|| !token.verify(new MACVerifier(signingKey))) {
				throw new InvalidAccessTokenException();
			}

			JWTClaimsSet claims = token.getJWTClaimsSet();
			Date issuedAtDate = claims.getIssueTime();
			Date expiresAtDate = claims.getExpirationTime();
			long userId = Long.parseLong(claims.getSubject());
			String email = claims.getStringClaim("email");
			String role = claims.getStringClaim("role");
			Instant issuedAt = issuedAtDate == null ? null : issuedAtDate.toInstant();
			Instant expiresAt = expiresAtDate == null ? null : expiresAtDate.toInstant();
			if (issuedAt == null || expiresAt == null || email == null || role == null
					|| issuedAt.isAfter(clock.instant()) || !expiresAt.isAfter(clock.instant())) {
				throw new InvalidAccessTokenException();
			}
			return new ParsedAccessToken(userId, email, role, issuedAt, expiresAt);
		} catch (java.text.ParseException | JOSEException exception) {
			throw new InvalidAccessTokenException();
		} catch (NumberFormatException exception) {
			throw new InvalidAccessTokenException();
		}
	}

	public long getAccessTokenLifetimeSeconds() {
		return accessTokenLifetime.toSeconds();
	}
}
