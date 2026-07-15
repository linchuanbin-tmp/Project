package com.agent.rag.controller;

import com.agent.rag.dto.EmbeddingReadiness;
import com.agent.rag.dto.EmbeddingRuntimeConfig;
import com.agent.rag.service.EmbeddingClient;
import com.agent.rag.service.EmbeddingRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class RagHealthController {

    private final EmbeddingClient embeddingClient;
    private final EmbeddingRuntimeConfigService embeddingConfigService;

    @Value("${rag.vector-store.provider:milvus}")
    private String vectorStoreProvider;

    @Value("${rag.llm.provider:mock}")
    private String llmProvider;

    @Value("${rag.llm.api-key:}")
    private String llmApiKey;

    @Value("${rag.llm.base-url:}")
    private String llmBaseUrl;

    @Value("${rag.llm.model:}")
    private String llmModel;

    @Value("${rag.llm.timeout-ms:30000}")
    private int llmTimeoutMs;

    @Value("${rag.llm.temperature:0.2}")
    private double llmTemperature;

    @Value("${rag.llm.max-tokens:1200}")
    private int llmMaxTokens;

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
        EmbeddingReadiness embeddingReadiness = embeddingClient.checkReadiness();
        EmbeddingRuntimeConfig embeddingConfig = embeddingConfigService.getCurrentConfig();
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "rag-agent");
        health.put("vectorStore", vectorStoreProvider);
        health.put("milvusCollection", embeddingConfig.getCollectionName());
        health.put("embeddingProfile", embeddingConfig.getProfile());
        health.put("embeddingIndexStatus", embeddingConfigService.getActiveIndexStatus());
        health.put("vectorMetric", vectorMetric);
        health.put("vectorIndex", vectorIndex);
        health.put("embeddingProvider", embeddingReadiness.getProvider());
        health.put("embeddingDim", embeddingReadiness.getDimension());
        health.put("embeddingTimeoutMs", embeddingReadiness.getTimeoutMs());
        health.put("embeddingEndpointConfigured", embeddingReadiness.getEndpointConfigured());
        health.put("embeddingApiKeyConfigured", embeddingReadiness.getApiKeyConfigured());
        health.put("embeddingModel", embeddingReadiness.getModel());
        health.put("embeddingReady", embeddingReadiness.getReady());
        health.put("embeddingProbed", embeddingReadiness.getProbed());
        health.put("embeddingActualDim", embeddingReadiness.getActualDimension());
        health.put("embeddingMessage", embeddingReadiness.getMessage());
        health.put("llmProvider", llmProvider);
        health.put("llmBaseUrlConfigured", StringUtils.hasText(llmBaseUrl));
        health.put("llmApiKeyConfigured", StringUtils.hasText(llmApiKey) && !"your_rag_llm_api_key".equals(llmApiKey));
        health.put("llmModel", llmModel);
        health.put("llmTimeoutMs", llmTimeoutMs);
        health.put("llmTemperature", llmTemperature);
        health.put("llmMaxTokens", llmMaxTokens);
        health.put("chunkSizeTokens", chunkSizeTokens);
        health.put("chunkOverlapTokens", chunkOverlapTokens);
        return health;
    }

    @GetMapping("/health/embedding")
    public EmbeddingReadiness embeddingHealth() {
        return embeddingClient.checkReadiness();
    }
}
