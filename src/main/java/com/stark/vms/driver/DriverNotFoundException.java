package com.stark.vms.driver;

public class DriverNotFoundException extends RuntimeException {
	public DriverNotFoundException() {
		super("Driver not found.");
	}
}
