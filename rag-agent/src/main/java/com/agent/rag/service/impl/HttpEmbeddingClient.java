package com.agent.rag.service.impl;

import com.agent.rag.dto.EmbeddingReadiness;
import com.agent.rag.dto.EmbeddingRuntimeConfig;
import com.agent.rag.service.EmbeddingClient;
import com.agent.rag.service.EmbeddingRuntimeConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class HttpEmbeddingClient implements EmbeddingClient {

    private static final String DASHSCOPE_ENDPOINT =
            "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";
    private static final String DASHSCOPE_DEFAULT_MODEL = "text-embedding-v4";

    private final EmbeddingRuntimeConfigService configService;

    @Override
    public List<Float> embed(String text) {
        EmbeddingRuntimeConfig config = configService.getCurrentConfig();
        if ("mock".equalsIgnoreCase(config.getProvider())) {
            return mockEmbedding(text, config.getDimension());
        }
        if (!isHttpProvider(config) && !isDashScopeProvider(config)) {
            throw new IllegalStateException("Unsupported embedding provider: " + config.getProvider());
        }
        if (isHttpProvider(config) && !StringUtils.hasText(config.getEndpoint())) {
            throw new IllegalStateException("Local embedding endpoint is not configured.");
        }
        if (isDashScopeProvider(config) && !StringUtils.hasText(config.getApiKey())) {
            throw new IllegalStateException("DashScope embedding API key is not configured.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(config.getApiKey())) {
            headers.setBearerAuth(config.getApiKey());
        }
        JsonNode response = callEmbeddingService(config, buildPayload(text, config), headers);
        List<Float> embedding = extractEmbedding(response);
        validateDimension(embedding, config);
        return embedding;
    }

    @Override
    public EmbeddingReadiness checkReadiness() {
        EmbeddingRuntimeConfig config = configService.getCurrentConfig();
        boolean endpointConfigured = StringUtils.hasText(config.getEndpoint()) || isDashScopeProvider(config);
        boolean apiKeyConfigured = StringUtils.hasText(config.getApiKey());

        if (!"mock".equalsIgnoreCase(config.getProvider())
                && !isHttpProvider(config) && !isDashScopeProvider(config)) {
            return readiness(config, false, false, null,
                    "Unsupported embedding provider: " + config.getProvider(),
                    endpointConfigured, apiKeyConfigured);
        }
        if (isHttpProvider(config) && !StringUtils.hasText(config.getEndpoint())) {
            return readiness(config, false, false, null,
                    "Local embedding endpoint is not configured.", false, apiKeyConfigured);
        }
        if (isDashScopeProvider(config) && !apiKeyConfigured) {
            return readiness(config, false, false, null,
                    "DashScope embedding API key is not configured.", true, false);
        }

        try {
            List<Float> probe = embed("RAG embedding readiness probe.");
            return readiness(config, true, true, probe.size(),
                    "Embedding provider is ready.", endpointConfigured, apiKeyConfigured);
        } catch (Exception e) {
            return readiness(config, false, true, null,
                    e.getMessage(), endpointConfigured, apiKeyConfigured);
        }
    }

    private JsonNode callEmbeddingService(
            EmbeddingRuntimeConfig config,
            Map<String, Object> payload,
            HttpHeaders headers
    ) {
        try {
            return restTemplate(config.getTimeoutMs()).postForObject(
                    resolveEndpoint(config), new HttpEntity<>(payload, headers), JsonNode.class);
        } catch (HttpStatusCodeException e) {
            String body = StringUtils.hasText(e.getResponseBodyAsString())
                    ? ": " + e.getResponseBodyAsString()
                    : "";
            throw new IllegalStateException("Embedding service request failed with HTTP "
                    + e.getStatusCode().value() + body, e);
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("Embedding service is unreachable or timed out after "
                    + config.getTimeoutMs() + "ms: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Embedding service request failed: " + e.getMessage(), e);
        }
    }

    private RestTemplate restTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int safeTimeout = timeoutMs > 0 ? timeoutMs : 10000;
        requestFactory.setConnectTimeout(safeTimeout);
        requestFactory.setReadTimeout(safeTimeout);
        return new RestTemplate(requestFactory);
    }

    private Map<String, Object> buildPayload(String text, EmbeddingRuntimeConfig config) {
        String safeText = text == null ? "" : text;
        if (isDashScopeProvider(config)) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", resolveModel(config));
            payload.put("input", Map.of("texts", List.of(safeText)));
            if (config.getDimension() > 0) {
                payload.put("parameters", Map.of("dimension", config.getDimension()));
            }
            return payload;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("input", safeText);
        payload.put("text", safeText);
        if (StringUtils.hasText(config.getModel())) {
            payload.put("model", config.getModel());
        }
        return payload;
    }

    private List<Float> extractEmbedding(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new IllegalStateException("Embedding service returned empty response.");
        }
        JsonNode embeddingNode = findEmbeddingArray(node);
        if (embeddingNode == null || !embeddingNode.isArray()) {
            throw new IllegalStateException("Embedding response does not contain an embedding array.");
        }
        List<Float> values = new ArrayList<>();
        for (JsonNode value : embeddingNode) {
            if (!value.isNumber()) {
                throw new IllegalStateException("Embedding response contains non-numeric value.");
            }
            values.add((float) value.asDouble());
        }
        if (values.isEmpty()) {
            throw new IllegalStateException("Embedding service returned empty vector.");
        }
        return values;
    }

    private JsonNode findEmbeddingArray(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray() && !node.isEmpty() && node.get(0).isNumber()) {
            return node;
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                JsonNode found = findEmbeddingArray(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void validateDimension(List<Float> embedding, EmbeddingRuntimeConfig config) {
        if (config.getDimension() > 0 && embedding.size() != config.getDimension()) {
            throw new IllegalStateException("Embedding dimension mismatch: expected "
                    + config.getDimension() + ", got " + embedding.size()
                    + ". Rebuild the active Milvus collection with the matching embedding profile.");
        }
    }

    private EmbeddingReadiness readiness(
            EmbeddingRuntimeConfig config,
            boolean ready,
            boolean probed,
            Integer actualDimension,
            String message,
            boolean endpointConfigured,
            boolean apiKeyConfigured
    ) {
        return EmbeddingReadiness.builder()
                .provider(config.getProfile())
                .ready(ready)
                .probed(probed)
                .message(message)
                .dimension(config.getDimension())
                .actualDimension(actualDimension)
                .model(resolveModel(config))
                .endpointConfigured(endpointConfigured)
                .apiKeyConfigured(apiKeyConfigured)
                .timeoutMs(config.getTimeoutMs())
                .build();
    }

    private boolean isHttpProvider(EmbeddingRuntimeConfig config) {
        return "http".equalsIgnoreCase(config.getProvider());
    }

    private boolean isDashScopeProvider(EmbeddingRuntimeConfig config) {
        return "qwen".equalsIgnoreCase(config.getProvider())
                || "dashscope".equalsIgnoreCase(config.getProvider());
    }

    private String resolveEndpoint(EmbeddingRuntimeConfig config) {
        if (isDashScopeProvider(config) && !StringUtils.hasText(config.getEndpoint())) {
            return DASHSCOPE_ENDPOINT;
        }
        return config.getEndpoint();
    }

    private String resolveModel(EmbeddingRuntimeConfig config) {
        if (isDashScopeProvider(config) && !StringUtils.hasText(config.getModel())) {
            return DASHSCOPE_DEFAULT_MODEL;
        }
        return config.getModel();
    }

    private List<Float> mockEmbedding(String text, int dimension) {
        if (dimension <= 0) {
            throw new IllegalStateException("RAG embedding dimension must be greater than 0.");
        }
        Random random = new Random(text == null ? 0 : text.hashCode());
        List<Float> values = new ArrayList<>(dimension);
        for (int i = 0; i < dimension; i++) {
            values.add(random.nextFloat() * 2 - 1);
        }
        return values;
    }
}
