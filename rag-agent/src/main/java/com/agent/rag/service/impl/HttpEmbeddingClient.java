package com.agent.rag.service.impl;

import com.agent.rag.service.EmbeddingClient;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
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
public class HttpEmbeddingClient implements EmbeddingClient {

    private RestTemplate restTemplate;

    @Value("${rag.embedding.provider:mock}")
    private String provider;

    @Value("${rag.embedding.endpoint:}")
    private String endpoint;

    @Value("${rag.embedding.api-key:}")
    private String apiKey;

    @Value("${rag.embedding.model:}")
    private String model;

    @Value("${rag.embedding.dimension:768}")
    private int dimension;

    @Value("${rag.embedding.timeout-ms:10000}")
    private int timeoutMs;

    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int safeTimeout = timeoutMs > 0 ? timeoutMs : 10000;
        requestFactory.setConnectTimeout(safeTimeout);
        requestFactory.setReadTimeout(safeTimeout);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @Override
    public List<Float> embed(String text) {
        if ("mock".equalsIgnoreCase(provider)) {
            return mockEmbedding(text);
        }
        if (!"http".equalsIgnoreCase(provider)) {
            throw new IllegalStateException("Unsupported embedding provider: " + provider);
        }
        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalStateException("RAG embedding endpoint is not configured.");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("input", text);
        payload.put("text", text);
        if (StringUtils.hasText(model)) {
            payload.put("model", model);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(apiKey)) {
            headers.setBearerAuth(apiKey);
        }
        JsonNode response = callEmbeddingService(payload, headers);
        List<Float> embedding = extractEmbedding(response);
        validateDimension(embedding);
        return embedding;
    }

    private JsonNode callEmbeddingService(Map<String, Object> payload, HttpHeaders headers) {
        try {
            return restTemplate.postForObject(endpoint, new HttpEntity<>(payload, headers), JsonNode.class);
        } catch (HttpStatusCodeException e) {
            String body = StringUtils.hasText(e.getResponseBodyAsString())
                    ? ": " + e.getResponseBodyAsString()
                    : "";
            throw new IllegalStateException("Embedding service request failed with HTTP "
                    + e.getStatusCode().value() + body, e);
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("Embedding service is unreachable or timed out after "
                    + timeoutMs + "ms: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Embedding service request failed: " + e.getMessage(), e);
        }
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
        if (node.isArray() && node.size() > 0 && node.get(0).isNumber()) {
            return node;
        }
        if (node.has("embedding")) {
            JsonNode direct = findEmbeddingArray(node.get("embedding"));
            if (direct != null) {
                return direct;
            }
        }
        if (node.has("vector")) {
            JsonNode direct = findEmbeddingArray(node.get("vector"));
            if (direct != null) {
                return direct;
            }
        }
        if (node.has("embeddings")) {
            JsonNode direct = findEmbeddingArray(node.get("embeddings"));
            if (direct != null) {
                return direct;
            }
        }
        if (node.has("vectors")) {
            JsonNode direct = findEmbeddingArray(node.get("vectors"));
            if (direct != null) {
                return direct;
            }
        }
        if (node.has("data")) {
            JsonNode direct = findEmbeddingArray(node.get("data"));
            if (direct != null) {
                return direct;
            }
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                JsonNode direct = findEmbeddingArray(item);
                if (direct != null) {
                    return direct;
                }
            }
        }
        return null;
    }

    private void validateDimension(List<Float> embedding) {
        if (dimension > 0 && embedding.size() != dimension) {
            throw new IllegalStateException("Embedding dimension mismatch: expected " + dimension + ", got " + embedding.size());
        }
    }

    private List<Float> mockEmbedding(String text) {
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
