package com.agent.rag.service.impl;

import com.agent.rag.dto.EmbeddingRuntimeConfig;
import com.agent.rag.dto.RagCitation;
import com.agent.rag.dto.RagPermissionSnapshot;
import com.agent.rag.dto.RagQueryRequest;
import com.agent.rag.dto.RagQueryResponse;
import com.agent.rag.dto.VectorSearchResult;
import com.agent.rag.entity.RagQueryLog;
import com.agent.rag.entity.SysDocument;
import com.agent.rag.mapper.RagQueryLogMapper;
import com.agent.rag.mapper.SysDocumentMapper;
import com.agent.rag.service.EmbeddingRuntimeConfigService;
import com.agent.rag.service.RagLlmClient;
import com.agent.rag.service.RagPermissionService;
import com.agent.rag.service.RagQueryService;
import com.agent.rag.service.RagVectorSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RagQueryServiceImpl implements RagQueryService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_NO_CONTEXT = "NO_CONTEXT";
    private static final String STATUS_LLM_FALLBACK = "LLM_FALLBACK";
    private static final String STATUS_FAIL = "FAIL";

    private final RagPermissionService ragPermissionService;
    private final RagVectorSearchService ragVectorSearchService;
    private final RagLlmClient ragLlmClient;
    private final RagQueryLogMapper ragQueryLogMapper;
    private final EmbeddingRuntimeConfigService embeddingConfigService;
    private final SysDocumentMapper sysDocumentMapper;

    @Override
    public RagQueryResponse query(RagQueryRequest request, String username, String rolesHeader) {
        Instant startedAt = Instant.now();
        String traceId = UUID.randomUUID().toString();
        int topK = normalizeTopK(request.getTopK());
        EmbeddingRuntimeConfig embeddingConfig = embeddingConfigService.getCurrentConfig();

        RagPermissionSnapshot permission = null;
        List<VectorSearchResult> filteredChunks = List.of();
        List<Long> blockedDocumentIds = List.of();
        String answer = null;
        String status = STATUS_SUCCESS;
        String message = "OK";

        try {
            permission = ragPermissionService.resolveAccessibleDocuments(username, rolesHeader);
            if (permission.getAllowedDocumentIds() == null || permission.getAllowedDocumentIds().isEmpty()) {
                status = STATUS_NO_CONTEXT;
                message = "No accessible documents for current user.";
                answer = "The current user has no accessible documents, so the question cannot be answered from the enterprise knowledge base.";
                return buildResponse(traceId, status, answer, List.of(), List.of(), List.of(), List.of(), topK, startedAt, message);
            }

            List<Long> allowedDocumentIds = permission.getAllowedDocumentIds();
            List<VectorSearchResult> candidates = embeddingConfigService.withProfile(
                    embeddingConfig.getProfile(),
                    () -> ragVectorSearchService.search(
                            request.getQuestion(),
                            Math.max(topK * 4, topK),
                            allowedDocumentIds)
            );
            QueryFilterResult filterResult = filterByPermission(candidates, allowedDocumentIds, topK);
            filteredChunks = filterResult.allowedChunks();
            blockedDocumentIds = filterResult.blockedDocumentIds();

            if (filteredChunks.isEmpty()) {
                status = STATUS_NO_CONTEXT;
                message = "No permission-safe retrieval context found.";
                answer = "No permission-safe context related to the question was found, so the answer cannot be confirmed from accessible documents.";
                return buildResponse(traceId, status, answer, List.of(), List.of(), blockedDocumentIds, List.of(), topK, startedAt, message);
            }

            List<RagCitation> citations = toCitations(filteredChunks);
            try {
                answer = ragLlmClient.generate(buildSafePrompt(request.getQuestion(), filteredChunks));
            } catch (Exception llmException) {
                status = STATUS_LLM_FALLBACK;
                message = "LLM generation failed; returning permission-safe retrieval context only: " + llmException.getMessage();
                answer = buildFallbackAnswer(filteredChunks, llmException.getMessage());
            }
            return buildResponse(
                    traceId,
                    status,
                    answer,
                    citations,
                    filteredChunks,
                    blockedDocumentIds,
                    retrievedDocumentIds(filteredChunks),
                    topK,
                    startedAt,
                    message
            );
        } catch (Exception e) {
            status = STATUS_FAIL;
            message = e.getMessage();
            answer = "RAG query failed: " + e.getMessage();
            return buildResponse(
                    traceId,
                    status,
                    answer,
                    toCitations(filteredChunks),
                    filteredChunks,
                    blockedDocumentIds,
                    retrievedDocumentIds(filteredChunks),
                    topK,
                    startedAt,
                    message
            );
        } finally {
            writeLog(request, permission, username, answer, filteredChunks, blockedDocumentIds,
                    embeddingConfig, topK, startedAt, status, message);
        }
    }

    private QueryFilterResult filterByPermission(List<VectorSearchResult> candidates, List<Long> allowedDocumentIds, int topK) {
        Set<Long> allowed = new LinkedHashSet<>(allowedDocumentIds);
        Set<Long> blocked = new LinkedHashSet<>();
        List<VectorSearchResult> filtered = new ArrayList<>();

        for (VectorSearchResult candidate : candidates) {
            Long documentId = candidate.getDocumentId();
            if (documentId == null || !allowed.contains(documentId)) {
                if (documentId != null) {
                    blocked.add(documentId);
                }
                continue;
            }
            filtered.add(candidate);
            if (filtered.size() >= topK) {
                break;
            }
        }
        return new QueryFilterResult(filtered, new ArrayList<>(blocked));
    }

    private String buildSafePrompt(String question, List<VectorSearchResult> chunks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an internal enterprise RAG assistant. Follow these rules strictly:\n");
        prompt.append("1. Answer only from the supplied permission-safe context. Do not invent facts outside the context.\n");
        prompt.append("2. If the context is insufficient, clearly say that the answer cannot be confirmed from accessible documents.\n");
        prompt.append("3. Cite the source number after relevant claims, for example [1].\n");
        prompt.append("4. Never mention or infer content from blocked or inaccessible documents.\n\n");
        prompt.append("User question:\n").append(question).append("\n\n");
        prompt.append("Permission-safe context:\n");

        int index = 1;
        for (VectorSearchResult chunk : chunks) {
            prompt.append("[").append(index).append("] ");
            prompt.append("documentId=").append(chunk.getDocumentId());
            prompt.append(", chunkId=").append(chunk.getChunkId());
            prompt.append(", chunkIndex=").append(chunk.getChunkIndex());
            prompt.append(", securityLevel=").append(chunk.getSecurityLevel());
            prompt.append("\n");
            prompt.append(chunk.getChunkText()).append("\n\n");
            index++;
        }
        return prompt.toString();
    }

    private String buildFallbackAnswer(List<VectorSearchResult> chunks, String reason) {
        StringBuilder answer = new StringBuilder();
        answer.append("LLM generation is currently unavailable, so only retrieved permission-safe context is returned.\n");
        answer.append("Reason: ").append(reason).append("\n\n");
        int index = 1;
        for (VectorSearchResult chunk : chunks) {
            answer.append("[").append(index).append("] ");
            answer.append("documentId=").append(chunk.getDocumentId());
            answer.append(", chunkId=").append(chunk.getChunkId());
            answer.append(", chunkIndex=").append(chunk.getChunkIndex()).append("\n");
            answer.append(snippet(chunk.getChunkText())).append("\n\n");
            index++;
        }
        return answer.toString().trim();
    }

    private List<RagCitation> toCitations(List<VectorSearchResult> chunks) {
        Map<Long, String> titlesByDocumentId = resolveDocumentTitles(chunks);
        return chunks.stream()
                .map(chunk -> RagCitation.builder()
                        .documentId(chunk.getDocumentId())
                        .documentTitle(titlesByDocumentId.get(chunk.getDocumentId()))
                        .chunkId(chunk.getChunkId())
                        .chunkIndex(chunk.getChunkIndex())
                        .score(chunk.getScore())
                        .snippet(snippet(chunk.getChunkText()))
                        .build())
                .collect(Collectors.toList());
    }

    private Map<Long, String> resolveDocumentTitles(List<VectorSearchResult> chunks) {
        List<Long> documentIds = chunks.stream()
                .map(VectorSearchResult::getDocumentId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        return sysDocumentMapper.selectBatchIds(documentIds).stream()
                .collect(Collectors.toMap(SysDocument::getId, SysDocument::getTitle, (left, right) -> left));
    }

    private RagQueryResponse buildResponse(
            String traceId,
            String status,
            String answer,
            List<RagCitation> citations,
            List<VectorSearchResult> chunks,
            List<Long> blockedDocumentIds,
            List<Long> retrievedDocumentIds,
            int topK,
            Instant startedAt,
            String message) {

        return RagQueryResponse.builder()
                .traceId(traceId)
                .status(status)
                .answer(answer)
                .citations(citations)
                .chunks(chunks)
                .retrievedDocumentIds(retrievedDocumentIds)
                .blockedDocumentIds(blockedDocumentIds)
                .topK(topK)
                .latencyMs(latencyMs(startedAt))
                .message(message)
                .build();
    }

    private void writeLog(
            RagQueryRequest request,
            RagPermissionSnapshot permission,
            String fallbackUsername,
            String answer,
            List<VectorSearchResult> chunks,
            List<Long> blockedDocumentIds,
            EmbeddingRuntimeConfig embeddingConfig,
            int topK,
            Instant startedAt,
            String status,
            String errorMessage) {

        RagQueryLog log = new RagQueryLog();
        log.setUserId(permission != null ? permission.getUserId() : 0L);
        log.setUsername(permission != null ? permission.getUsername() : fallbackUsername);
        log.setQuestion(request.getQuestion());
        log.setAnswer(answer);
        log.setRetrievedDocIds(joinIds(retrievedDocumentIds(chunks)));
        log.setBlockedDocIds(joinIds(blockedDocumentIds));
        log.setEmbeddingProfile(embeddingConfig.getProfile());
        log.setEmbeddingModel(embeddingConfig.getModel());
        log.setVectorCollection(embeddingConfig.getCollectionName());
        log.setTopK(topK);
        log.setLatencyMs(latencyMs(startedAt));
        log.setStatus(status);
        log.setErrorMsg((STATUS_FAIL.equals(status) || STATUS_LLM_FALLBACK.equals(status)) ? errorMessage : null);
        log.setCreateTime(LocalDateTime.now());
        ragQueryLogMapper.insert(log);
    }

    private List<Long> retrievedDocumentIds(List<VectorSearchResult> chunks) {
        return chunks.stream()
                .map(VectorSearchResult::getDocumentId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
    }

    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private int normalizeTopK(Integer topK) {
        return topK == null || topK <= 0 ? 5 : Math.min(topK, 20);
    }

    private int latencyMs(Instant startedAt) {
        return Math.toIntExact(Math.min(Duration.between(startedAt, Instant.now()).toMillis(), Integer.MAX_VALUE));
    }

    private String snippet(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240);
    }

    private record QueryFilterResult(List<VectorSearchResult> allowedChunks, List<Long> blockedDocumentIds) {
    }
}
