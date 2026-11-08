package com.stark.vms.common.error;

public class DuplicateEmailException extends RuntimeException {

	public DuplicateEmailException() {
		super("Email already in use.");
	}
}
