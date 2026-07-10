package com.agent.rag.service.impl;

import com.agent.rag.dto.VectorSearchResult;
import com.agent.rag.service.EmbeddingClient;
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

    @Override
    public List<VectorSearchResult> search(String question, Integer topK) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("Question must not be empty.");
        }
        int safeTopK = topK == null || topK <= 0 ? 5 : Math.min(topK, 20);
        return vectorStoreService.search(embeddingClient.embed(question), safeTopK);
    }
}
