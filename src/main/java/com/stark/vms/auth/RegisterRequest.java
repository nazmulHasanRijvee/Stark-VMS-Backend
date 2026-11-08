package com.stark.vms.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.stark.vms.common.validation.ValidEmailAddress;

public record RegisterRequest(
		@NotBlank @Size(min = 2, max = 100) String fullName,
		@NotBlank @ValidEmailAddress @Size(max = 254) String email,
		@NotBlank @Size(min = 6, max = 72) String password) {
}
