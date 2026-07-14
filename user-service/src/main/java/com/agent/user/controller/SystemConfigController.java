package com.agent.user.controller;

import com.agent.user.dto.Result;
import com.agent.user.entity.SysConfig;
import com.agent.user.mapper.SysConfigMapper;
import com.agent.user.utils.ApiKeyEncryptor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SysConfigMapper sysConfigMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ApiKeyEncryptor apiKeyEncryptor;

    @Value("${app.deepseek.api-key}")
    private String defaultApiKey;

    @Value("${app.deepseek-official.api-key}")
    private String defaultDeepseekApiKey;

    // Lazy-loaded .env reader
    private static volatile Dotenv dotenv;
    private static Dotenv dotenv() {
        if (dotenv == null) {
            synchronized (SystemConfigController.class) {
                if (dotenv == null) {
                    try {
                        dotenv = Dotenv.configure()
                                .directory("../")
                                .ignoreIfMissing()
                                .load();
                    } catch (Exception e) {
                        dotenv = null;
                    }
                }
            }
        }
        return dotenv;
    }

    private String envOrDotenv(String key) {
        String val = System.getenv(key);
        if (val != null) return val;
        Dotenv d = dotenv();
        return d != null ? d.get(key) : null;
    }

    private static final String CONFIG_KEY_SESSION_TIMEOUT = "session_timeout";
    private static final String REDIS_KEY_SESSION_TIMEOUT  = "sys:config:session_timeout";
    private static final long   DEFAULT_SESSION_TIMEOUT    = 30L;

    private static final String CONFIG_KEY_AI_PROVIDER  = "ai_provider";
    private static final String REDIS_KEY_AI_PROVIDER   = "sys:config:ai_provider";
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * GET /user/config/session-timeout
     * Returns the current session inactivity timeout in minutes.
     * Admin-only.
     */
    @GetMapping("/session-timeout")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result<Long> getSessionTimeout() {
        long timeout = resolveTimeout();
        return Result.success(timeout);
    }

    /**
     * PUT /user/config/session-timeout
     * Updates the session inactivity timeout in minutes.
     * Admin-only.
     * Body: { "timeout": 45 }
     */
    @PutMapping("/session-timeout")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result<String> updateSessionTimeout(@RequestBody Map<String, Object> body) {
        Object raw = body.get("timeout");
        if (raw == null) {
            return Result.error(400, "Missing 'timeout' field in request body.");
        }

        long timeout;
        try {
            timeout = Long.parseLong(raw.toString());
        } catch (NumberFormatException e) {
            return Result.error(400, "Invalid 'timeout' value — must be a positive integer.");
        }

        if (timeout < 1 || timeout > 1440) {
            return Result.error(400, "Timeout must be between 1 and 1440 minutes.");
        }

        // Update database
        SysConfig existing = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getParamKey, CONFIG_KEY_SESSION_TIMEOUT)
        );
        if (existing == null) {
            SysConfig config = new SysConfig();
            config.setParamKey(CONFIG_KEY_SESSION_TIMEOUT);
            config.setParamValue(String.valueOf(timeout));
            config.setDescription("Session inactivity timeout in minutes");
            sysConfigMapper.insert(config);
        } else {
            sysConfigMapper.update(null,
                    new LambdaUpdateWrapper<SysConfig>()
                            .eq(SysConfig::getParamKey, CONFIG_KEY_SESSION_TIMEOUT)
                            .set(SysConfig::getParamValue, String.valueOf(timeout))
            );
        }

        // Refresh Redis cache
        stringRedisTemplate.opsForValue().set(REDIS_KEY_SESSION_TIMEOUT, String.valueOf(timeout));

        return Result.success("Session timeout updated to " + timeout + " minute(s).");
    }

    /**
     * Helper: resolve timeout value from Redis cache → DB → default (30).
     */
    private long resolveTimeout() {
        String cached = stringRedisTemplate.opsForValue().get(REDIS_KEY_SESSION_TIMEOUT);
        if (cached != null) {
            try { return Long.parseLong(cached); } catch (NumberFormatException ignored) {}
        }
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getParamKey, CONFIG_KEY_SESSION_TIMEOUT)
        );
        long value = DEFAULT_SESSION_TIMEOUT;
        if (config != null) {
            try { value = Long.parseLong(config.getParamValue()); } catch (NumberFormatException ignored) {}
        }
        stringRedisTemplate.opsForValue().set(REDIS_KEY_SESSION_TIMEOUT, String.valueOf(value));
        return value;
    }

    // ── AI Provider 配置 ─────────────────────────────────────────────

    /**
     * GET /user/config/ai-provider
     * Returns the current AI provider config. Any user can read (no auth needed).
     * api_key is NOT returned — the backend decrypts and uses it internally only.
     */
    @GetMapping("/ai-provider")
    public Result<Map<String, Object>> getAiProvider() {
        String cached = stringRedisTemplate.opsForValue().get(REDIS_KEY_AI_PROVIDER);
        if (cached != null) {
            try { return Result.success(sanitizeForClient(objectMapper.readValue(cached, Map.class))); } catch (JsonProcessingException ignored) {}
        }
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getParamKey, CONFIG_KEY_AI_PROVIDER)
        );
        Map<String, Object> result = new HashMap<>();
        if (config != null) {
            try {
                result = objectMapper.readValue(config.getParamValue(), Map.class);
            } catch (JsonProcessingException e) {
                // fall through to default
            }
        }
        if (result.isEmpty()) {
            result.put("provider", "xunfei");
            result.put("baseUrl", "https://maas-api.cn-huabei-1.xf-yun.com/v2");
            result.put("model", "xopdeepseekv32");
        }
        try {
            stringRedisTemplate.opsForValue().set(REDIS_KEY_AI_PROVIDER, objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException ignored) {}
        // Strip api_key before returning to client
        return Result.success(sanitizeForClient(result));
    }

    /**
     * PUT /user/config/ai-provider
     * Updates the AI provider config. Admin-only.
     * Body: { "provider": "deepseek", "baseUrl": "https://api.deepseek.com", "model": "deepseek-chat", "apiKey": "sk-xxx" }
     * apiKey is encrypted before storing.
     */
    @PutMapping("/ai-provider")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result<String> updateAiProvider(@RequestBody Map<String, Object> body) {
        String provider = body.get("provider") != null ? body.get("provider").toString() : null;
        String baseUrl  = body.get("baseUrl")  != null ? body.get("baseUrl").toString()  : null;
        String model    = body.get("model")    != null ? body.get("model").toString()    : null;
        String apiKey   = body.get("apiKey")   != null ? body.get("apiKey").toString()   : null;

        if (provider == null || provider.isBlank()) {
            return Result.error(400, "Missing 'provider' field.");
        }

        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("provider", provider);
        valueMap.put("baseUrl", baseUrl != null ? baseUrl : "");
        valueMap.put("model", model != null ? model : "");
        // Encrypt API key if provided
        if (apiKey != null && !apiKey.isBlank()) {
            valueMap.put("apiKeyEncrypted", apiKeyEncryptor.encrypt(apiKey));
        }

        String jsonValue;
        try {
            jsonValue = objectMapper.writeValueAsString(valueMap);
        } catch (JsonProcessingException e) {
            return Result.error(500, "Failed to serialize AI provider config.");
        }

        SysConfig existing = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getParamKey, CONFIG_KEY_AI_PROVIDER)
        );
        if (existing == null) {
            SysConfig cfg = new SysConfig();
            cfg.setParamKey(CONFIG_KEY_AI_PROVIDER);
            cfg.setParamValue(jsonValue);
            cfg.setDescription("AI model provider configuration (provider/baseUrl/model)");
            sysConfigMapper.insert(cfg);
        } else {
            sysConfigMapper.update(null,
                    new LambdaUpdateWrapper<SysConfig>()
                            .eq(SysConfig::getParamKey, CONFIG_KEY_AI_PROVIDER)
                            .set(SysConfig::getParamValue, jsonValue)
            );
        }

        stringRedisTemplate.opsForValue().set(REDIS_KEY_AI_PROVIDER, jsonValue);

        return Result.success("AI provider updated to " + provider + ".");
    }

    /**
     * POST /user/config/ai-provider/test
     * Test LLM connectivity with given baseUrl, model, apiKey.
     * Body: { baseUrl, model, message, apiKey? }
     */
    @PostMapping("/ai-provider/test")
    public Result<Map<String, Object>> testConnection(@RequestBody Map<String, Object> body) {
        String baseUrl  = body.get("baseUrl")  != null ? body.get("baseUrl").toString()  : "";
        String model    = body.get("model")    != null ? body.get("model").toString()    : "deepseek-chat";
        String message  = body.get("message")  != null ? body.get("message").toString()  : "hi";
        String apiKey   = body.get("apiKey")   != null ? body.get("apiKey").toString()   : null;

        // Resolve API key: provided > .env / yml default
        if (apiKey == null || apiKey.isBlank()) {
            if (baseUrl != null && (baseUrl.contains("api.deepseek.com") || baseUrl.contains("11434"))) {
                apiKey = envOrDotenv("DEEPSEEK_OFFICIAL_API_KEY");
                if (apiKey == null || apiKey.isBlank()) apiKey = defaultDeepseekApiKey;
            } else {
                apiKey = envOrDotenv("DEEPSEEK_API_KEY");
                if (apiKey == null || apiKey.isBlank()) apiKey = defaultApiKey;
            }
        }

        if (apiKey == null || apiKey.isBlank()) {
            var errMap = new HashMap<String, Object>();
            errMap.put("ok", false);
            errMap.put("error", "No API key configured. Please enter one in the dialog.");
            return Result.success(errMap);
        }

        if (baseUrl.isBlank()) {
            return Result.error(400, "baseUrl is required");
        }

        try {
            var restTemplate = new RestTemplate();
            var headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            var requestMap = new HashMap<String, Object>();
            requestMap.put("model", model);
            requestMap.put("messages", List.of(
                Map.of("role", "user", "content", message)
            ));
            requestMap.put("max_tokens", 20);

            var entity = new HttpEntity<>(requestMap, headers);
            var response = restTemplate.postForEntity(
                baseUrl + "/chat/completions", entity, String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                String body2 = response.getBody();
                JsonNode root = objectMapper.readTree(body2);
                String reply = root.path("choices").get(0).path("message").path("content").asText("(empty)");
                var resultMap = new HashMap<String, Object>();
                resultMap.put("ok", true);
                resultMap.put("reply", reply);
                return Result.success(resultMap);
            }
            return Result.error("LLM returned " + response.getStatusCodeValue());
        } catch (Exception e) {
            var errorMap = new HashMap<String, Object>();
            errorMap.put("ok", false);
            errorMap.put("error", e.getMessage());
            return Result.success(errorMap);
        }
    }

    /** Remove apiKeyEncrypted from map before sending to client */
    private Map<String, Object> sanitizeForClient(Map<String, Object> map) {
        map.remove("apiKeyEncrypted");
        return map;
    }

    /**
     * Utility for internal use: resolve the decrypted API key at runtime.
     * Falls back to environment variable if no DB config exists.
     */
    public String resolveApiKey() {
        String cached = stringRedisTemplate.opsForValue().get(REDIS_KEY_AI_PROVIDER);
        Map<String, Object> map = new HashMap<>();
        if (cached != null) {
            try { map = objectMapper.readValue(cached, Map.class); } catch (JsonProcessingException ignored) {}
        }
        String encrypted = (String) map.get("apiKeyEncrypted");
        if (encrypted != null && !encrypted.isBlank()) {
            return apiKeyEncryptor.decrypt(encrypted);
        }
        // Fallback: env var / .env file > yml default (xunfei vs deepseek)
        String key = envOrDotenv("DEEPSEEK_API_KEY");
        if (key != null) return key;

        String provider = (String) map.get("provider");
        if (provider != null && (provider.contains("deepseek") || provider.equals("ollama"))) {
            return defaultDeepseekApiKey;
        }
        return defaultApiKey;
    }

    /**
     * GET /user/config/ai-provider/internal
     * Internal-only: returns full AI provider config WITH decrypted API key.
     * Called by other agents (Tool Agent, Code Agent Python, RAG Agent) on startup.
     * Not exposed through Gateway — only accessible within Docker internal network.
     */
    @GetMapping("/ai-provider/internal")
    public Result<Map<String, Object>> getAiProviderInternal() {
        Map<String, Object> result = buildAiProviderMap();
        // Inject the decrypted API key
        String decryptedKey = resolveApiKey();
        if (decryptedKey != null && !decryptedKey.isBlank()) {
            result.put("apiKey", decryptedKey);
        }
        return Result.success(result);
    }

    private Map<String, Object> buildAiProviderMap() {
        String cached = stringRedisTemplate.opsForValue().get(REDIS_KEY_AI_PROVIDER);
        Map<String, Object> map = new HashMap<>();
        if (cached != null) {
            try { map.putAll(objectMapper.readValue(cached, Map.class)); } catch (JsonProcessingException ignored) {}
        }
        if (map.isEmpty()) {
            SysConfig config = sysConfigMapper.selectOne(
                    new LambdaQueryWrapper<SysConfig>()
                            .eq(SysConfig::getParamKey, CONFIG_KEY_AI_PROVIDER)
            );
            if (config != null) {
                try { map.putAll(objectMapper.readValue(config.getParamValue(), Map.class)); } catch (JsonProcessingException ignored) {}
            }
        }
        if (map.isEmpty()) {
            map.put("provider", "xunfei");
            map.put("baseUrl", "https://maas-api.cn-huabei-1.xf-yun.com/v2");
            map.put("model", "xopdeepseekv32");
        }
        return map;
    }
}
