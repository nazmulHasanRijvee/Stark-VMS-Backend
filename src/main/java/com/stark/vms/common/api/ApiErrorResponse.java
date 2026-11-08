package com.stark.vms.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
		boolean success,
		String message,
		Instant timestamp,
		Map<String, String> fieldErrors) {

	public static ApiErrorResponse of(String message, Map<String, String> fieldErrors) {
		return new ApiErrorResponse(false, message, Instant.now(),
				fieldErrors == null || fieldErrors.isEmpty() ? null : fieldErrors);
	}
}
