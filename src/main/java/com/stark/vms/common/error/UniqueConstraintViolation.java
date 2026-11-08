package com.stark.vms.common.error;

import java.sql.SQLException;

public final class UniqueConstraintViolation {

	private UniqueConstraintViolation() {
	}

	public static boolean isUniqueConstraintViolation(Throwable error) {
		for (Throwable current = error; current != null; current = current.getCause()) {
			if (current instanceof SQLException sqlException
					&& "23505".equals(sqlException.getSQLState())) {
				return true;
			}
		}
		return false;
	}
}
