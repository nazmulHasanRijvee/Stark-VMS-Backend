package com.stark.vms.documents;

import java.util.List;

public interface DocumentStorage {
	void upload(String objectKey, byte[] contents, String contentType);
	void deleteObjects(List<String> objectKeys);
	String createSignedDownloadUrl(String objectKey, int expiresInSeconds);
}
