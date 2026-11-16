package com.stark.vms.documents;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.stark.vms.common.error.DomainValidationException;

public enum DocumentType {
	REGISTRATION("Registration"),
	TAX_TOKEN("Tax Token"),
	INSURANCE("Insurance"),
	FITNESS_CERTIFICATE("Fitness Certificate"),
	OTHER("Other");

	private final String displayValue;

	DocumentType(String displayValue) {
		this.displayValue = displayValue;
	}

	@JsonValue
	public String getDisplayValue() {
		return displayValue;
	}

	@JsonCreator
	public static DocumentType fromDisplayValue(String value) {
		for (DocumentType type : values()) {
			if (type.displayValue.equals(value)) {
				return type;
			}
		}
		throw new DomainValidationException("Document type must be Registration, Tax Token, Insurance, Fitness Certificate, or Other.");
	}
}
