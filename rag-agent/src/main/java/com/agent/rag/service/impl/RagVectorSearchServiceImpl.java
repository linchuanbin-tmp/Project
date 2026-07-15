package com.agent.rag.service.impl;

import com.agent.rag.dto.VectorSearchResult;
import com.agent.rag.service.EmbeddingClient;
import com.agent.rag.service.EmbeddingRuntimeConfigService;
import com.agent.rag.service.RagVectorSearchService;
import com.agent.rag.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RagVectorSearchServiceImpl implements RagVectorSearchService {

    private final EmbeddingClient embeddingClient;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingRuntimeConfigService embeddingConfigService;

    @Override
    public List<VectorSearchResult> search(String question, Integer topK) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("Question must not be empty.");
        }
        if (!embeddingConfigService.isActiveIndexReady()) {
            throw new IllegalStateException("Active embedding profile index is not ready. Current status: "
                    + embeddingConfigService.getActiveIndexStatus()
                    + ". Please rebuild the RAG index after switching embedding profiles.");
        }
        int safeTopK = topK == null || topK <= 0 ? 5 : Math.min(topK, 20);
        return embeddingConfigService.withCurrentProfile(
                () -> vectorStoreService.search(embeddingClient.embed(question), safeTopK)
        );
    }
}
