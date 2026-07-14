package com.agent.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class RagIndexSyncClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${rag.service-uri:${RAG_SERVICE_URI:http://localhost:8085}}")
    private String ragServiceUri;

    public void indexDocumentAfterCommit(Long documentId) {
        runAfterCommit(() -> indexDocument(documentId));
    }

    public void deleteDocumentIndexAfterCommit(Long documentId) {
        runAfterCommit(() -> deleteDocumentIndex(documentId));
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void indexDocument(Long documentId) {
        try {
            restTemplate.postForObject(normalizedBaseUrl() + "/rag/index/document/" + documentId, null, String.class);
            log.info("Triggered RAG reindex for document {}", documentId);
        } catch (Exception e) {
            log.warn("Failed to trigger RAG reindex for document {}: {}", documentId, e.getMessage());
        }
    }

    private void deleteDocumentIndex(Long documentId) {
        try {
            restTemplate.delete(normalizedBaseUrl() + "/rag/index/document/" + documentId);
            log.info("Triggered RAG index deletion for document {}", documentId);
        } catch (Exception e) {
            log.warn("Failed to trigger RAG index deletion for document {}: {}", documentId, e.getMessage());
        }
    }

    private String normalizedBaseUrl() {
        if (ragServiceUri == null || ragServiceUri.isBlank()) {
            return "http://localhost:8085";
        }
        return ragServiceUri.endsWith("/") ? ragServiceUri.substring(0, ragServiceUri.length() - 1) : ragServiceUri;
    }
}
