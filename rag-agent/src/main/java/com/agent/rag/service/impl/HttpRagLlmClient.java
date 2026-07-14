package com.agent.rag.service.impl;

import com.agent.rag.service.RagLlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HttpRagLlmClient implements RagLlmClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${rag.llm.provider:mock}")
    private String provider;

    @Value("${rag.llm.api-key:}")
    private String apiKey;

    @Value("${rag.llm.base-url:}")
    private String baseUrl;

    @Value("${rag.llm.model:}")
    private String model;

    @Override
    public String generate(String prompt) {
        if ("mock".equalsIgnoreCase(provider)) {
            return "Mock RAG answer based on retrieved context. " +
                    "Please switch RAG_LLM_PROVIDER to http and configure RAG_LLM_API_KEY for real generation.";
        }
        if (!"http".equalsIgnoreCase(provider)) {
            throw new IllegalStateException("Unsupported RAG LLM provider: " + provider);
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("RAG LLM API key is not configured.");
        }
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("RAG LLM base URL is not configured.");
        }
        if (!StringUtils.hasText(model)) {
            throw new IllegalStateException("RAG LLM model is not configured.");
        }

        JsonNode response = restTemplate.postForObject(chatCompletionsUrl(), new HttpEntity<>(requestBody(prompt), headers()), JsonNode.class);
        return extractAnswer(response);
    }

    private Map<String, Object> requestBody(String prompt) {
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("role", "system");
        system.put("content", "You are an enterprise RAG assistant. Answer only from the supplied context and cite sources.");

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("role", "user");
        user.put("content", prompt);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(system, user));
        body.put("temperature", 0.2);
        return body;
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }

    private String chatCompletionsUrl() {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }

    private String extractAnswer(JsonNode response) {
        if (response == null || response.isNull()) {
            throw new IllegalStateException("RAG LLM returned empty response.");
        }
        JsonNode choices = response.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("RAG LLM response does not contain choices.");
        }
        JsonNode first = choices.get(0);
        JsonNode message = first.get("message");
        if (message != null && message.has("content")) {
            return message.get("content").asText();
        }
        if (first.has("text")) {
            return first.get("text").asText();
        }
        throw new IllegalStateException("RAG LLM response does not contain answer content.");
    }
}
