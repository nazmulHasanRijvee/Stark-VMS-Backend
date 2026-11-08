package com.stark.vms.vehicle;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class VehicleStatusConverter implements AttributeConverter<VehicleStatus, String> {

	@Override
	public String convertToDatabaseColumn(VehicleStatus status) {
		return status == null ? null : status.getDisplayValue();
	}

	@Override
	public VehicleStatus convertToEntityAttribute(String value) {
		return value == null ? null : VehicleStatus.fromDisplayValue(value);
	}
}
