package com.stark.vms.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class ValidEmailAddressValidator implements ConstraintValidator<ValidEmailAddress, String> {

	private static final Pattern EMAIL_FORMAT = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || value.isBlank() || EMAIL_FORMAT.matcher(value.strip()).matches();
	}
}
