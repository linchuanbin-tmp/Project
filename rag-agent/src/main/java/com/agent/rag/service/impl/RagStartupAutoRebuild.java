package com.agent.rag.service.impl;

import com.agent.rag.service.EmbeddingRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ensures the active RAG embedding profile has a ready index on startup.
 * If the index status is not READY, a rebuild is triggered automatically in the background.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagStartupAutoRebuild {

    private final EmbeddingRuntimeConfigService configService;
    private final RagIndexServiceImpl indexService;

    private static final AtomicBoolean autoRebuildTriggered = new AtomicBoolean(false);

    @PostConstruct
    void ensureIndexReady() {
        if (!autoRebuildTriggered.compareAndSet(false, true)) {
            return;
        }

        String profile = configService.getActiveProfile().getId();
        String status = configService.getActiveIndexStatus();
        log.info("RAG startup check: profile={}, indexStatus={}", profile, status);

        if (EmbeddingRuntimeConfigService.STATUS_READY.equalsIgnoreCase(status)) {
            log.info("RAG index is ready -- no rebuild needed.");
            return;
        }

        log.info("RAG index is not ready, triggering automatic rebuild for profile: {}", profile);
        try {
            indexService.rebuildAll();
        } catch (Exception e) {
            log.warn("Auto-rebuild trigger failed: {}", e.getMessage());
        }
    }
}
