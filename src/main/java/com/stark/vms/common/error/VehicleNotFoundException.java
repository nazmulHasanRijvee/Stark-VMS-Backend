package com.stark.vms.common.error;

public class VehicleNotFoundException extends RuntimeException {

	public VehicleNotFoundException() {
		super("Vehicle not found.");
	}
}
