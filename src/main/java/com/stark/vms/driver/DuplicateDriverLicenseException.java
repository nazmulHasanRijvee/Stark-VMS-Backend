package com.stark.vms.driver;

public class DuplicateDriverLicenseException extends RuntimeException {
	public DuplicateDriverLicenseException() {
		super("A driver with this license number already exists.");
	}
}
