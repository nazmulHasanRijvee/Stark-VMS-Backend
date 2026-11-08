package com.stark.vms.auth;

import java.time.Instant;

public record ParsedAccessToken(Long userId, String email, String role,
		Instant issuedAt, Instant expiresAt) {
}
