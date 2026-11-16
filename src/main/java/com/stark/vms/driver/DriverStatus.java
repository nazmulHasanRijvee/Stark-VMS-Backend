package com.stark.vms.driver;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.stark.vms.common.error.DomainValidationException;

public enum DriverStatus {
	ACTIVE("Active"),
	INACTIVE("Inactive");

	private final String displayValue;

	DriverStatus(String displayValue) {
		this.displayValue = displayValue;
	}

	@JsonValue
	public String getDisplayValue() {
		return displayValue;
	}

	@JsonCreator
	public static DriverStatus fromDisplayValue(String value) {
		for (DriverStatus status : values()) {
			if (status.displayValue.equals(value)) return status;
		}
		throw new DomainValidationException("Status must be Active or Inactive.");
	}
}
