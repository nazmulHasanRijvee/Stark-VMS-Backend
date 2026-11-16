package com.stark.vms.common.error;

public class StorageUnavailableException extends RuntimeException {
	public StorageUnavailableException() {
		super("Document storage is unavailable. Please try again later.");
	}

	public StorageUnavailableException(Throwable cause) {
		super("Document storage is unavailable. Please try again later.", cause);
	}
}
