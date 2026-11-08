package com.stark.vms.auth;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
