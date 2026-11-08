package com.stark.vms.vehicle;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum VehicleStatus {
	ACTIVE("Active"),
	IN_SERVICE("In Service"),
	INACTIVE("Inactive");

	private final String displayValue;

	VehicleStatus(String displayValue) {
		this.displayValue = displayValue;
	}

	@JsonValue
	public String getDisplayValue() {
		return displayValue;
	}

	@JsonCreator
	public static VehicleStatus fromDisplayValue(String value) {
		for (VehicleStatus status : values()) {
			if (status.displayValue.equals(value)) {
				return status;
			}
		}
		throw new IllegalArgumentException("Unsupported vehicle status");
	}
}
