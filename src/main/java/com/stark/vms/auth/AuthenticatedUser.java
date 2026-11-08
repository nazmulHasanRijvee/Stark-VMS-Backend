package com.stark.vms.auth;

public record AuthenticatedUser(Long id, String email, String role) {
}
