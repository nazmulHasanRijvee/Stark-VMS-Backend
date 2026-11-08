package com.stark.vms.common.error;

public class InvalidAccessTokenException extends RuntimeException {

	public InvalidAccessTokenException() {
		super("The access token is invalid or expired.");
	}
}
