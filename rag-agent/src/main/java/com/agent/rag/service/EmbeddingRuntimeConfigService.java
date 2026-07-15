package com.agent.rag.service;

import com.agent.rag.dto.EmbeddingProfile;
import com.agent.rag.dto.EmbeddingProfileActivationResponse;
import com.agent.rag.dto.EmbeddingRuntimeConfig;
import com.agent.rag.entity.SysConfig;
import com.agent.rag.mapper.SysConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

@Service
@Slf4j
public class EmbeddingRuntimeConfigService {

    public static final String STATUS_READY = "READY";
    public static final String STATUS_REBUILD_REQUIRED = "REBUILD_REQUIRED";
    public static final String STATUS_REBUILDING = "REBUILDING";
    public static final String STATUS_FAILED = "FAILED";

    private static final String ACTIVE_PROFILE_KEY = "rag_embedding_active_profile";
    private static final String INDEX_STATUS_KEY_PREFIX = "rag_embedding_index_status_";
    private static final String LOCAL_PROFILE_ID = "local-bge-m3";
    private static final String QWEN_PROFILE_ID = "qwen-v4";
    private static final String MOCK_PROFILE_ID = "mock";
    private static final String DEFAULT_QWEN_ENDPOINT =
            "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";

    private final SysConfigMapper sysConfigMapper;
    private final ThreadLocal<EmbeddingRuntimeConfig> profileContext = new ThreadLocal<>();

    @Value("${rag.embedding.active-profile:}")
    private String configuredActiveProfile;

    @Value("${rag.embedding.provider:mock}")
    private String fallbackProvider;

    @Value("${rag.embedding.endpoint:}")
    private String fallbackEndpoint;

    @Value("${rag.embedding.api-key:}")
    private String fallbackApiKey;

    @Value("${rag.embedding.model:}")
    private String fallbackModel;

    @Value("${rag.embedding.dimension:768}")
    private int fallbackDimension;

    @Value("${rag.embedding.timeout-ms:10000}")
    private int fallbackTimeoutMs;

    @Value("${rag.vector-store.milvus.collection-name:rag_document_chunks}")
    private String fallbackCollectionName;

    @Value("${rag.embedding.profiles.local.endpoint:http://localhost:8091/embed}")
    private String localEndpoint;

    @Value("${rag.embedding.profiles.local.model:BAAI/bge-m3}")
    private String localModel;

    @Value("${rag.embedding.profiles.local.dimension:1024}")
    private int localDimension;

    @Value("${rag.embedding.profiles.local.timeout-ms:30000}")
    private int localTimeoutMs;

    @Value("${rag.embedding.profiles.local.collection-name:rag_document_chunks_bge_m3}")
    private String localCollectionName;

    @Value("${rag.embedding.profiles.qwen.endpoint:}")
    private String qwenEndpoint;

    @Value("${rag.embedding.profiles.qwen.api-key:${RAG_EMBEDDING_API_KEY:}}")
    private String qwenApiKey;

    @Value("${rag.embedding.profiles.qwen.model:text-embedding-v4}")
    private String qwenModel;

    @Value("${rag.embedding.profiles.qwen.dimension:1024}")
    private int qwenDimension;

    @Value("${rag.embedding.profiles.qwen.timeout-ms:30000}")
    private int qwenTimeoutMs;

    @Value("${rag.embedding.profiles.qwen.collection-name:rag_document_chunks_qwen_v4_1024}")
    private String qwenCollectionName;

    public EmbeddingRuntimeConfigService(SysConfigMapper sysConfigMapper) {
        this.sysConfigMapper = sysConfigMapper;
    }

    public EmbeddingRuntimeConfig getCurrentConfig() {
        EmbeddingRuntimeConfig scoped = profileContext.get();
        return scoped != null ? scoped : configForProfile(getActiveProfileId());
    }

    public List<EmbeddingProfile> listProfiles() {
        String activeProfileId = getActiveProfileId();
        return List.of(
                toProfile(LOCAL_PROFILE_ID, activeProfileId),
                toProfile(QWEN_PROFILE_ID, activeProfileId)
        );
    }

    public EmbeddingProfile getActiveProfile() {
        return toProfile(getActiveProfileId(), getActiveProfileId());
    }

    public EmbeddingProfile getProfile(String profileId) {
        return toProfile(normalizeProfileId(profileId), getActiveProfileId());
    }

    public EmbeddingProfileActivationResponse activateProfile(String profileId) {
        String normalized = normalizeProfileId(profileId);
        if (!LOCAL_PROFILE_ID.equals(normalized) && !QWEN_PROFILE_ID.equals(normalized)) {
            throw new IllegalArgumentException("Unsupported embedding profile: " + profileId);
        }

        String previous = getActiveProfileId();
        boolean changed = !Objects.equals(previous, normalized);

        if (changed) {
            // Only persist the switch and reset index status when the profile actually changes.
            // Clicking Save on the same profile should be a no-op that preserves the current index status.
            upsertConfig(ACTIVE_PROFILE_KEY, normalized, "Active RAG embedding profile");
            upsertConfig(indexStatusKey(normalized), STATUS_REBUILD_REQUIRED,
                    "RAG embedding index status for " + normalized);
        }
        log.info("Activated RAG embedding profile: {} (previous: {}, changed: {})", normalized, previous, changed);

        String status = changed ? STATUS_REBUILD_REQUIRED : getIndexStatus(normalized);
        boolean rebuildRequired = !STATUS_READY.equalsIgnoreCase(status);
        return EmbeddingProfileActivationResponse.builder()
                .activeProfileId(normalized)
                .previousProfileId(previous)
                .indexStatus(status)
                .rebuildRequired(rebuildRequired)
                .message(changed
                        ? "Embedding profile activated. Rebuild the RAG index before querying."
                        : "Embedding profile unchanged. Index status: " + status + ".")
                .build();
    }

    public String getActiveIndexStatus() {
        return getIndexStatus(getActiveProfileId());
    }

    public boolean isActiveIndexReady() {
        return STATUS_READY.equalsIgnoreCase(getActiveIndexStatus());
    }

    public void markActiveIndexRebuilding() {
        upsertConfig(indexStatusKey(getActiveProfileId()), STATUS_REBUILDING,
                "RAG embedding index status for " + getActiveProfileId());
    }

    public void markActiveIndexReady() {
        upsertConfig(indexStatusKey(getActiveProfileId()), STATUS_READY,
                "RAG embedding index status for " + getActiveProfileId());
    }

    public void markActiveIndexFailed(String message) {
        String value = STATUS_FAILED;
        if (StringUtils.hasText(message)) {
            value += ": " + message;
        }
        upsertConfig(indexStatusKey(getActiveProfileId()), truncate(value, 480),
                "RAG embedding index status for " + getActiveProfileId());
    }

    public <T> T withCurrentProfile(Supplier<T> supplier) {
        return withProfile(getCurrentConfig(), supplier);
    }

    public <T> T withProfile(String profileId, Supplier<T> supplier) {
        return withProfile(configForProfile(normalizeProfileId(profileId)), supplier);
    }

    private <T> T withProfile(EmbeddingRuntimeConfig config, Supplier<T> supplier) {
        EmbeddingRuntimeConfig previous = profileContext.get();
        profileContext.set(config);
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                profileContext.remove();
            } else {
                profileContext.set(previous);
            }
        }
    }

    private EmbeddingProfile toProfile(String profileId, String activeProfileId) {
        EmbeddingRuntimeConfig config = configForProfile(profileId);
        String indexStatus = getIndexStatus(profileId);
        return EmbeddingProfile.builder()
                .id(profileId)
                .label(labelFor(profileId))
                .provider(config.getProvider())
                .endpoint(config.getEndpoint())
                .endpointConfigured(StringUtils.hasText(config.getEndpoint()) || isQwenProfile(profileId))
                .apiKeyConfigured(StringUtils.hasText(config.getApiKey()))
                .model(config.getModel())
                .dimension(config.getDimension())
                .timeoutMs(config.getTimeoutMs())
                .collectionName(config.getCollectionName())
                .active(profileId.equals(activeProfileId))
                .indexStatus(stripStatusMessage(indexStatus))
                .indexMessage(indexStatus)
                .build();
    }

    private EmbeddingRuntimeConfig configForProfile(String profileId) {
        String normalized = normalizeProfileId(profileId);
        if (LOCAL_PROFILE_ID.equals(normalized)) {
            return EmbeddingRuntimeConfig.builder()
                    .profile(LOCAL_PROFILE_ID)
                    .provider("http")
                    .endpoint(localEndpoint)
                    .apiKey("")
                    .model(localModel)
                    .dimension(positiveOrDefault(localDimension, 1024))
                    .timeoutMs(positiveOrDefault(localTimeoutMs, 30000))
                    .collectionName(defaultText(localCollectionName, "rag_document_chunks_bge_m3"))
                    .build();
        }
        if (QWEN_PROFILE_ID.equals(normalized)) {
            return EmbeddingRuntimeConfig.builder()
                    .profile(QWEN_PROFILE_ID)
                    .provider("qwen")
                    .endpoint(defaultText(qwenEndpoint, DEFAULT_QWEN_ENDPOINT))
                    .apiKey(defaultText(qwenApiKey, fallbackApiKey))
                    .model(defaultText(qwenModel, "text-embedding-v4"))
                    .dimension(positiveOrDefault(qwenDimension, 1024))
                    .timeoutMs(positiveOrDefault(qwenTimeoutMs, 30000))
                    .collectionName(defaultText(qwenCollectionName, "rag_document_chunks_qwen_v4_1024"))
                    .build();
        }
        return fallbackConfig();
    }

    private EmbeddingRuntimeConfig fallbackConfig() {
        String profile = "mock".equalsIgnoreCase(fallbackProvider)
                ? MOCK_PROFILE_ID
                : (isQwenProvider(fallbackProvider) ? QWEN_PROFILE_ID : LOCAL_PROFILE_ID);
        return EmbeddingRuntimeConfig.builder()
                .profile(profile)
                .provider(fallbackProvider)
                .endpoint(fallbackEndpoint)
                .apiKey(fallbackApiKey)
                .model(fallbackModel)
                .dimension(positiveOrDefault(fallbackDimension, 768))
                .timeoutMs(positiveOrDefault(fallbackTimeoutMs, 10000))
                .collectionName(defaultText(fallbackCollectionName, "rag_document_chunks"))
                .build();
    }

    private String getActiveProfileId() {
        String stored = getConfig(ACTIVE_PROFILE_KEY);
        if (StringUtils.hasText(stored)) {
            return normalizeProfileId(stored);
        }
        if (StringUtils.hasText(configuredActiveProfile)) {
            return normalizeProfileId(configuredActiveProfile);
        }
        if (isQwenProvider(fallbackProvider)) {
            return QWEN_PROFILE_ID;
        }
        if ("http".equalsIgnoreCase(fallbackProvider)) {
            return LOCAL_PROFILE_ID;
        }
        return LOCAL_PROFILE_ID;
    }

    private String getIndexStatus(String profileId) {
        String value = getConfig(indexStatusKey(profileId));
        return StringUtils.hasText(value) ? value : STATUS_REBUILD_REQUIRED;
    }

    private String getConfig(String key) {
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getParamKey, key)
                        .last("LIMIT 1")
        );
        return config == null ? null : config.getParamValue();
    }

    private void upsertConfig(String key, String value, String description) {
        SysConfig existing = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getParamKey, key)
                        .last("LIMIT 1")
        );
        if (existing == null) {
            SysConfig config = new SysConfig();
            config.setParamKey(key);
            config.setParamValue(value);
            config.setDescription(description);
            sysConfigMapper.insert(config);
            return;
        }
        sysConfigMapper.update(null,
                new LambdaUpdateWrapper<SysConfig>()
                        .eq(SysConfig::getParamKey, key)
                        .set(SysConfig::getParamValue, value)
                        .set(SysConfig::getDescription, description)
        );
    }

    private String normalizeProfileId(String profileId) {
        String value = profileId == null ? "" : profileId.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "local", "bge", "bge-m3", "local-bge", "local-bge-m3" -> LOCAL_PROFILE_ID;
            case "qwen", "dashscope", "qwen-v4", "text-embedding-v4" -> QWEN_PROFILE_ID;
            case "mock" -> MOCK_PROFILE_ID;
            default -> value;
        };
    }

    private String labelFor(String profileId) {
        return isQwenProfile(profileId) ? "Qwen text-embedding-v4" : "Local BGE-M3";
    }

    private String indexStatusKey(String profileId) {
        return INDEX_STATUS_KEY_PREFIX + normalizeProfileId(profileId);
    }

    private String stripStatusMessage(String indexStatus) {
        int split = indexStatus == null ? -1 : indexStatus.indexOf(':');
        return split < 0 ? indexStatus : indexStatus.substring(0, split);
    }

    private boolean isQwenProfile(String profileId) {
        return QWEN_PROFILE_ID.equals(normalizeProfileId(profileId));
    }

    private boolean isQwenProvider(String provider) {
        return "qwen".equalsIgnoreCase(provider) || "dashscope".equalsIgnoreCase(provider);
    }

    private int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : Math.max(defaultValue, 1);
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
