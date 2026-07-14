package com.agent.tool.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DeepSeekService {

    @Value("${ai.deepseek.api-key}")
    private String fallbackApiKey;

    @Value("${ai.deepseek.base-url}")
    private String fallbackBaseUrl;

    @Value("${ai.deepseek.model}")
    private String fallbackModel;

    @Value("${user-service.url:http://user-service:8081}")
    private String userServiceUrl;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    // Cached unified config from user-service
    private volatile Map<String, String> unifiedConfig = new ConcurrentHashMap<>();

    public DeepSeekService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        refreshConfig();
    }

    private void refreshConfig() {
        try {
            String url = userServiceUrl + "/user/config/ai-provider/internal";
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            JsonNode root = objectMapper.readTree(resp.getBody());
            if (root.path("code").asInt() == 200 && !root.path("data").isMissingNode()) {
                Map<String, String> cfg = objectMapper.convertValue(root.path("data"), new TypeReference<Map<String, String>>() {});
                unifiedConfig.putAll(cfg);
                log.info("AI config loaded from user-service: provider={}, model={}", cfg.get("provider"), cfg.get("model"));
            }
        } catch (Exception e) {
            log.warn("Failed to load AI config from user-service, using fallback: {}", e.getMessage());
        }
    }

    private String getBaseUrl() {
        String url = unifiedConfig.get("baseUrl");
        return (url != null && !url.isBlank()) ? url : fallbackBaseUrl;
    }

    private String getModel() {
        String model = unifiedConfig.get("model");
        return (model != null && !model.isBlank()) ? model : fallbackModel;
    }

    private String getApiKey() {
        String key = unifiedConfig.get("apiKey");
        return (key != null && !key.isBlank()) ? key : fallbackApiKey;
    }

    public String chat(String systemPrompt, String userContent) {
        String apiKey = getApiKey();
        String baseUrl = getBaseUrl();
        String model = getModel();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userContent)
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.3,
                "max_tokens", 2048
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/chat/completions",
                    request,
                    String.class
            );

            String responseBody = response.getBody();
            log.debug("DeepSeek response: {}", responseBody);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                return choices.get(0).get("message").get("content").asText();
            }
            throw new RuntimeException("DeepSeek returned unexpected format");

        } catch (Exception e) {
            log.error("DeepSeek call failed", e);
            throw new RuntimeException("AI service unavailable: " + e.getMessage());
        }
    }
}