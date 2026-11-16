package com.stark.vms.driver;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class DriverStatusConverter implements AttributeConverter<DriverStatus, String> {
	@Override
	public String convertToDatabaseColumn(DriverStatus status) {
		return status == null ? null : status.getDisplayValue();
	}

	@Override
	public DriverStatus convertToEntityAttribute(String value) {
		return value == null ? null : DriverStatus.fromDisplayValue(value);
	}
}
