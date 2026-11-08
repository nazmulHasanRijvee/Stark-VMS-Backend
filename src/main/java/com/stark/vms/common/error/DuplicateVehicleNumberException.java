package com.stark.vms.common.error;

public class DuplicateVehicleNumberException extends RuntimeException {

	public DuplicateVehicleNumberException() {
		super("A vehicle with this registration number already exists.");
	}
}
