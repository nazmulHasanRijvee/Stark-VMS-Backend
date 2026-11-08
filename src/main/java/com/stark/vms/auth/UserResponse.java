package com.stark.vms.auth;

public record UserResponse(Long id, String fullName, String email, String role) {

	public static UserResponse from(AppUser user) {
		return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
	}
}
