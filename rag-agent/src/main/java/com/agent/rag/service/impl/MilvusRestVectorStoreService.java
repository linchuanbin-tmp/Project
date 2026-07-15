package com.agent.rag.service.impl;

import com.agent.rag.dto.EmbeddingRuntimeConfig;
import com.agent.rag.dto.VectorRecord;
import com.agent.rag.dto.VectorSearchResult;
import com.agent.rag.service.EmbeddingRuntimeConfigService;
import com.agent.rag.service.VectorStoreService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class MilvusRestVectorStoreService implements VectorStoreService {

    private final EmbeddingRuntimeConfigService configService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final Set<String> initializedCollections = ConcurrentHashMap.newKeySet();

    @Value("${rag.vector-store.provider:milvus}")
    private String provider;

    @Value("${rag.vector-store.milvus.host:localhost}")
    private String host;

    @Value("${rag.vector-store.milvus.port:19530}")
    private int port;

    @Value("${rag.vector-store.milvus.token:}")
    private String token;

    @Value("${rag.vector-store.milvus.metric-type:COSINE}")
    private String metricType;

    @Value("${rag.vector-store.milvus.index-type:AUTOINDEX}")
    private String indexType;

    @PostConstruct
    public void onStartup() {
        if (isMilvusEnabled()) {
            try {
                initializeCollection();
            } catch (Exception e) {
                log.warn("Milvus collection initialization skipped: {}", e.getMessage());
            }
        }
    }

    @Override
    public void initializeCollection() {
        initializeCollection(configService.getCurrentConfig());
    }

    @Override
    public void upsert(List<VectorRecord> records) {
        if (!isMilvusEnabled() || records == null || records.isEmpty()) {
            return;
        }
        EmbeddingRuntimeConfig config = configService.getCurrentConfig();
        ensureCollection(config);

        List<Map<String, Object>> data = new ArrayList<>();
        for (VectorRecord record : records) {
            data.add(toMilvusEntity(record));
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("collectionName", config.getCollectionName());
        request.put("data", data);
        JsonNode response = post("/v2/vectordb/entities/upsert", request);
        ensureSuccess(response, "upsert vectors into Milvus");
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        if (!isMilvusEnabled() || documentId == null) {
            return;
        }
        EmbeddingRuntimeConfig config = configService.getCurrentConfig();
        ensureCollection(config);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("collectionName", config.getCollectionName());
        request.put("filter", "document_id == " + documentId);
        JsonNode response = post("/v2/vectordb/entities/delete", request);
        ensureSuccess(response, "delete document vectors from Milvus");
    }

    @Override
    public List<VectorSearchResult> search(List<Float> queryEmbedding, int topK) {
        if (!isMilvusEnabled()) {
            return List.of();
        }
        if (queryEmbedding == null || queryEmbedding.isEmpty()) {
            throw new IllegalArgumentException("Query embedding is empty.");
        }
        EmbeddingRuntimeConfig config = configService.getCurrentConfig();
        ensureCollection(config);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("collectionName", config.getCollectionName());
        request.put("data", List.of(queryEmbedding));
        request.put("limit", Math.max(1, topK));
        request.put("outputFields", List.of(
                "vector_id", "document_id", "chunk_id", "chunk_index",
                "dept_id", "security_level", "chunk_text"
        ));

        JsonNode response = post("/v2/vectordb/entities/search", request);
        ensureSuccess(response, "search Milvus vectors");
        return parseSearchResults(response);
    }

    private synchronized void initializeCollection(EmbeddingRuntimeConfig config) {
        if (!isMilvusEnabled() || initializedCollections.contains(config.getCollectionName())) {
            return;
        }
        if (!collectionExists(config.getCollectionName())) {
            JsonNode response = post("/v2/vectordb/collections/create", buildCreateCollectionRequest(config));
            ensureSuccess(response, "create Milvus collection " + config.getCollectionName());
        }
        loadCollection(config.getCollectionName());
        initializedCollections.add(config.getCollectionName());
    }

    private void ensureCollection(EmbeddingRuntimeConfig config) {
        if (!initializedCollections.contains(config.getCollectionName())) {
            initializeCollection(config);
        }
    }

    private boolean collectionExists(String collectionName) {
        JsonNode response = post("/v2/vectordb/collections/list", Map.of());
        ensureSuccess(response, "list Milvus collections");
        return containsCollectionName(response.get("data"), collectionName);
    }

    private void loadCollection(String collectionName) {
        JsonNode response = post("/v2/vectordb/collections/load",
                Map.of("collectionName", collectionName));
        ensureSuccess(response, "load Milvus collection " + collectionName);
    }

    private Map<String, Object> buildCreateCollectionRequest(EmbeddingRuntimeConfig config) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("collectionName", config.getCollectionName());
        request.put("schema", buildSchema(config.getDimension()));
        request.put("indexParams", buildIndexParams());
        return request;
    }

    private Map<String, Object> buildSchema(int dimension) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("autoId", false);
        schema.put("enableDynamicField", false);
        schema.put("fields", List.of(
                varcharField("vector_id", true, 256),
                int64Field("document_id"),
                int64Field("chunk_id"),
                int64Field("chunk_index"),
                int64Field("dept_id"),
                int64Field("security_level"),
                varcharField("content_hash", false, 128),
                varcharField("chunk_text", false, 65535),
                vectorField(dimension)
        ));
        return schema;
    }

    private List<Map<String, Object>> buildIndexParams() {
        Map<String, Object> embeddingIndex = new LinkedHashMap<>();
        embeddingIndex.put("fieldName", "embedding");
        embeddingIndex.put("indexName", "embedding_index");
        embeddingIndex.put("metricType", metricType);
        embeddingIndex.put("indexType", indexType);
        if ("HNSW".equalsIgnoreCase(indexType)) {
            embeddingIndex.put("params", Map.of("M", "16", "efConstruction", "200"));
        }
        return List.of(embeddingIndex);
    }

    private Map<String, Object> varcharField(String name, boolean primary, int maxLength) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("fieldName", name);
        field.put("dataType", "VarChar");
        field.put("isPrimary", primary);
        field.put("elementTypeParams", Map.of("max_length", String.valueOf(maxLength)));
        return field;
    }

    private Map<String, Object> int64Field(String name) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("fieldName", name);
        field.put("dataType", "Int64");
        return field;
    }

    private Map<String, Object> vectorField(int dimension) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("fieldName", "embedding");
        field.put("dataType", "FloatVector");
        field.put("elementTypeParams", Map.of("dim", String.valueOf(dimension)));
        return field;
    }

    private Map<String, Object> toMilvusEntity(VectorRecord record) {
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("vector_id", record.getVectorId());
        entity.put("document_id", record.getDocumentId());
        entity.put("chunk_id", record.getChunkId() == null ? 0L : record.getChunkId());
        entity.put("chunk_index", record.getChunkIndex() == null ? 0L : record.getChunkIndex().longValue());
        entity.put("dept_id", record.getDeptId() == null ? 0L : record.getDeptId());
        entity.put("security_level", record.getSecurityLevel() == null ? 1L : record.getSecurityLevel().longValue());
        entity.put("content_hash", record.getContentHash());
        entity.put("chunk_text", truncate(record.getChunkText(), 65535));
        entity.put("embedding", record.getEmbedding());
        return entity;
    }

    private List<VectorSearchResult> parseSearchResults(JsonNode response) {
        List<VectorSearchResult> results = new ArrayList<>();
        JsonNode data = response.get("data");
        if (data == null || !data.isArray()) {
            return results;
        }
        for (JsonNode groupOrItem : data) {
            if (groupOrItem.isArray()) {
                for (JsonNode item : groupOrItem) {
                    results.add(toSearchResult(item));
                }
            } else {
                results.add(toSearchResult(groupOrItem));
            }
        }
        return results;
    }

    private VectorSearchResult toSearchResult(JsonNode item) {
        JsonNode entity = item.has("entity") ? item.get("entity") : item;
        return VectorSearchResult.builder()
                .vectorId(asText(entity, "vector_id"))
                .documentId(asLong(entity, "document_id"))
                .chunkId(asLong(entity, "chunk_id"))
                .chunkIndex(asInteger(entity, "chunk_index"))
                .deptId(asLong(entity, "dept_id"))
                .securityLevel(asInteger(entity, "security_level"))
                .score(item.has("distance") ? item.get("distance").asDouble() : item.path("score").asDouble())
                .chunkText(asText(entity, "chunk_text"))
                .build();
    }

    private boolean containsCollectionName(JsonNode node, String collectionName) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isTextual()) {
            return collectionName.equals(node.asText());
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (containsCollectionName(item, collectionName)) {
                    return true;
                }
            }
        }
        if (node.isObject()) {
            if (node.has("collectionName") && collectionName.equals(node.get("collectionName").asText())) {
                return true;
            }
            if (node.has("collectionNames")) {
                return containsCollectionName(node.get("collectionNames"), collectionName);
            }
        }
        return false;
    }

    private JsonNode post(String path, Map<String, Object> payload) {
        return restTemplate.postForObject(baseUrl() + path, new HttpEntity<>(payload, headers()), JsonNode.class);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(token)) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private String baseUrl() {
        return "http://" + host + ":" + port;
    }

    private boolean isMilvusEnabled() {
        return "milvus".equalsIgnoreCase(provider);
    }

    private void ensureSuccess(JsonNode response, String operation) {
        if (response == null || response.isNull()) {
            throw new IllegalStateException("Milvus returned empty response during " + operation + ".");
        }
        int code = response.path("code").asInt(0);
        if (code != 0) {
            throw new IllegalStateException("Failed to " + operation + ": " + response);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String asText(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private Long asLong(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asLong() : null;
    }

    private Integer asInteger(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asInt() : null;
    }
}
