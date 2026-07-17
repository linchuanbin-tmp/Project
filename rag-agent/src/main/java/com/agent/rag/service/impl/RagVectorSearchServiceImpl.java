package com.agent.rag.service.impl;

import com.agent.rag.dto.VectorSearchResult;
import com.agent.rag.service.EmbeddingClient;
import com.agent.rag.service.EmbeddingRuntimeConfigService;
import com.agent.rag.service.RagVectorSearchService;
import com.agent.rag.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagVectorSearchServiceImpl implements RagVectorSearchService {

    private final EmbeddingClient embeddingClient;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingRuntimeConfigService embeddingConfigService;

    @Override
    public List<VectorSearchResult> search(String question, Integer topK, List<Long> allowedDocumentIds) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("Question must not be empty.");
        }
        if (allowedDocumentIds == null || allowedDocumentIds.isEmpty()) {
            return List.of();
        }
        if (!embeddingConfigService.isActiveIndexReady()) {
            log.warn("RAG index not ready (status: {}). Returning empty results.", embeddingConfigService.getActiveIndexStatus());
            return List.of();
        }
        int safeTopK = topK == null || topK <= 0 ? 5 : Math.min(topK, 20);
        return embeddingConfigService.withCurrentProfile(
                () -> vectorStoreService.search(embeddingClient.embed(question), safeTopK, allowedDocumentIds)
        );
    }
}
