package com.agent.rag.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/rag")
public class RagHealthController {

    @Value("${rag.vector-store.provider:milvus}")
    private String vectorStoreProvider;

    @Value("${rag.embedding.provider:mock}")
    private String embeddingProvider;

    @Value("${rag.embedding.endpoint:}")
    private String embeddingEndpoint;

    @Value("${rag.embedding.api-key:}")
    private String embeddingApiKey;

    @Value("${rag.embedding.model:}")
    private String embeddingModel;

    @Value("${rag.embedding.dimension:768}")
    private int embeddingDimension;

    @Value("${rag.embedding.timeout-ms:10000}")
    private int embeddingTimeoutMs;

    @Value("${rag.llm.provider:mock}")
    private String llmProvider;

    @Value("${rag.llm.api-key:}")
    private String llmApiKey;

    @Value("${rag.llm.base-url:}")
    private String llmBaseUrl;

    @Value("${rag.llm.model:}")
    private String llmModel;

    @Value("${rag.vector-store.milvus.collection-name:rag_document_chunks}")
    private String milvusCollection;

    @Value("${rag.vector-store.milvus.metric-type:COSINE}")
    private String vectorMetric;

    @Value("${rag.vector-store.milvus.index-type:HNSW}")
    private String vectorIndex;

    @Value("${rag.index.chunk-size-tokens:700}")
    private int chunkSizeTokens;

    @Value("${rag.index.chunk-overlap-tokens:100}")
    private int chunkOverlapTokens;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "rag-agent");
        health.put("vectorStore", vectorStoreProvider);
        health.put("milvusCollection", milvusCollection);
        health.put("vectorMetric", vectorMetric);
        health.put("vectorIndex", vectorIndex);
        health.put("embeddingProvider", embeddingProvider);
        health.put("embeddingDim", embeddingDimension);
        health.put("embeddingTimeoutMs", embeddingTimeoutMs);
        health.put("embeddingEndpointConfigured", StringUtils.hasText(embeddingEndpoint));
        health.put("embeddingApiKeyConfigured", StringUtils.hasText(embeddingApiKey));
        health.put("embeddingModel", embeddingModel);
        health.put("llmProvider", llmProvider);
        health.put("llmBaseUrlConfigured", StringUtils.hasText(llmBaseUrl));
        health.put("llmApiKeyConfigured", StringUtils.hasText(llmApiKey) && !"your_rag_llm_api_key".equals(llmApiKey));
        health.put("llmModel", llmModel);
        health.put("chunkSizeTokens", chunkSizeTokens);
        health.put("chunkOverlapTokens", chunkOverlapTokens);
        return health;
    }
}
