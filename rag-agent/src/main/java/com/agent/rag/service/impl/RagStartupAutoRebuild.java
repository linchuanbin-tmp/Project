package com.agent.rag.service.impl;

import com.agent.rag.service.EmbeddingRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Ensures the active RAG embedding profile has a ready index on startup.
 * The rebuild runs synchronously during startup so the system is fully ready
 * before accepting any requests.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagStartupAutoRebuild {

    private final EmbeddingRuntimeConfigService configService;
    private final RagIndexServiceImpl indexService;

    @PostConstruct
    void ensureIndexReady() {
        String profile = configService.getActiveProfile().getId();
        String status = configService.getActiveIndexStatus();
        log.info("RAG startup check: profile={}, indexStatus={}", profile, status);

        if (EmbeddingRuntimeConfigService.STATUS_READY.equalsIgnoreCase(status)) {
            log.info("RAG index is ready -- no rebuild needed.");
            return;
        }

        log.info("RAG index not ready, running synchronous rebuild for profile: {}", profile);
        try {
            indexService.rebuildAllSync();
        } catch (Exception e) {
            log.error("Synchronous RAG rebuild failed during startup: {}", e.getMessage(), e);
        }
    }
}
