package com.agent.rag.service.impl;

import com.agent.rag.service.EmbeddingClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class HttpEmbeddingClient implements EmbeddingClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${rag.embedding.provider:http}")
    private String provider;

    @Value("${rag.embedding.endpoint:}")
    private String endpoint;

    @Value("${rag.embedding.dimension:768}")
    private int dimension;

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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        JsonNode response = restTemplate.postForObject(endpoint, new HttpEntity<>(payload, headers), JsonNode.class);
        List<Float> embedding = extractEmbedding(response);
        validateDimension(embedding);
        return embedding;
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
            values.add((float) value.asDouble());
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
            throw new IllegalStateException("Embedding dimension mismatch. expected=" + dimension + ", actual=" + embedding.size());
        }
    }

    private List<Float> mockEmbedding(String text) {
        Random random = new Random(text == null ? 0 : text.hashCode());
        List<Float> values = new ArrayList<>(dimension);
        for (int i = 0; i < dimension; i++) {
            values.add(random.nextFloat() * 2 - 1);
        }
        return values;
    }
}
