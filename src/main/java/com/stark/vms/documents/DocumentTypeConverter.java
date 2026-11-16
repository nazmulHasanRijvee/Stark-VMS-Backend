package com.stark.vms.documents;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class DocumentTypeConverter implements AttributeConverter<DocumentType, String> {
	@Override
	public String convertToDatabaseColumn(DocumentType type) {
		return type == null ? null : type.getDisplayValue();
	}

	@Override
	public DocumentType convertToEntityAttribute(String value) {
		return value == null ? null : DocumentType.fromDisplayValue(value);
	}
}
