package com.agent.rag.service;

import com.agent.rag.dto.StoredDocument;

public interface DocumentStorageService {

    StoredDocument storeOriginal(Long kbId, Long sourceDocumentId, String fileName, String contentType, byte[] content);

    byte[] readOriginal(String bucket, String objectKey);
}
