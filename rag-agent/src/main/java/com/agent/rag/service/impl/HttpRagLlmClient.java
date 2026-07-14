package com.agent.rag.service.impl;

import com.agent.rag.service.RagLlmClient;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HttpRagLlmClient implements RagLlmClient {

    private RestTemplate restTemplate;

    @Value("${rag.llm.provider:mock}")
    private String provider;

    @Value("${rag.llm.api-key:}")
    private String apiKey;

    @Value("${rag.llm.base-url:}")
    private String baseUrl;

    @Value("${rag.llm.model:}")
    private String model;

    @Value("${rag.llm.timeout-ms:30000}")
    private int timeoutMs;

    @Value("${rag.llm.temperature:0.2}")
    private double temperature;

    @Value("${rag.llm.max-tokens:1200}")
    private int maxTokens;

    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int safeTimeout = timeoutMs > 0 ? timeoutMs : 30000;
        requestFactory.setConnectTimeout(safeTimeout);
        requestFactory.setReadTimeout(safeTimeout);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @Override
    public String generate(String prompt) {
        if ("mock".equalsIgnoreCase(provider)) {
            return "Mock RAG answer based on retrieved context. " +
                    "Please switch RAG_LLM_PROVIDER to http and configure RAG_LLM_API_KEY for real generation.";
        }
        if (!"http".equalsIgnoreCase(provider)) {
            throw new IllegalStateException("Unsupported RAG LLM provider: " + provider);
        }
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("RAG LLM base URL is not configured.");
        }
        if (!StringUtils.hasText(model)) {
            throw new IllegalStateException("RAG LLM model is not configured.");
        }

        JsonNode response = callLlmService(prompt);
        return extractAnswer(response);
    }

    private JsonNode callLlmService(String prompt) {
        try {
            return restTemplate.postForObject(chatCompletionsUrl(), new HttpEntity<>(requestBody(prompt), headers()), JsonNode.class);
        } catch (HttpStatusCodeException e) {
            String body = StringUtils.hasText(e.getResponseBodyAsString())
                    ? ": " + e.getResponseBodyAsString()
                    : "";
            throw new IllegalStateException("RAG LLM request failed with HTTP "
                    + e.getStatusCode().value() + body, e);
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("RAG LLM service is unreachable or timed out after "
                    + timeoutMs + "ms: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("RAG LLM request failed: " + e.getMessage(), e);
        }
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
        body.put("temperature", temperature);
        if (maxTokens > 0) {
            body.put("max_tokens", maxTokens);
        }
        return body;
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(apiKey)) {
            headers.setBearerAuth(apiKey);
        }
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
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            JsonNode first = choices.get(0);
            JsonNode message = first.get("message");
            if (message != null && message.has("content")) {
                return validateAnswer(message.get("content").asText());
            }
            if (first.has("text")) {
                return validateAnswer(first.get("text").asText());
            }
        }
        if (response.has("answer")) {
            return validateAnswer(response.get("answer").asText());
        }
        if (response.has("content")) {
            return validateAnswer(response.get("content").asText());
        }
        if (response.has("text")) {
            return validateAnswer(response.get("text").asText());
        }
        throw new IllegalStateException("RAG LLM response does not contain answer content.");
    }

    private String validateAnswer(String answer) {
        if (!StringUtils.hasText(answer)) {
            throw new IllegalStateException("RAG LLM returned empty answer content.");
        }
        return answer.trim();
    }
}
