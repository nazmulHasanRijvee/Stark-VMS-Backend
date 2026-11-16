package com.stark.vms.common.error;

public class DocumentNotFoundException extends RuntimeException {
	public DocumentNotFoundException() {
		super("Vehicle document not found.");
	}
}
