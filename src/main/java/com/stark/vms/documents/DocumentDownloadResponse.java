package com.stark.vms.documents;

public record DocumentDownloadResponse(Long documentId, String url, int expiresInSeconds) {
}
