package com.stark.vms.auth;

import java.time.Instant;

public record IssuedAccessToken(String value, Instant expiresAt) {
}
