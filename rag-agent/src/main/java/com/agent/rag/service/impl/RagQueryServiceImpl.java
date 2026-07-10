package com.agent.rag.service.impl;

import com.agent.rag.dto.RagCitation;
import com.agent.rag.dto.RagPermissionSnapshot;
import com.agent.rag.dto.RagQueryRequest;
import com.agent.rag.dto.RagQueryResponse;
import com.agent.rag.dto.VectorSearchResult;
import com.agent.rag.entity.RagQueryLog;
import com.agent.rag.mapper.RagQueryLogMapper;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RagQueryServiceImpl implements RagQueryService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_NO_CONTEXT = "NO_CONTEXT";
    private static final String STATUS_FAIL = "FAIL";

    private final RagPermissionService ragPermissionService;
    private final RagVectorSearchService ragVectorSearchService;
    private final RagLlmClient ragLlmClient;
    private final RagQueryLogMapper ragQueryLogMapper;

    @Override
    public RagQueryResponse query(RagQueryRequest request, String username, String rolesHeader) {
        Instant startedAt = Instant.now();
        String traceId = UUID.randomUUID().toString();
        int topK = normalizeTopK(request.getTopK());

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
                answer = "当前用户没有可访问的文档，因此无法基于企业知识库回答该问题。";
                return buildResponse(traceId, status, answer, List.of(), List.of(), List.of(), List.of(), topK, startedAt, message);
            }

            List<VectorSearchResult> candidates = ragVectorSearchService.search(request.getQuestion(), Math.max(topK * 4, topK));
            QueryFilterResult filterResult = filterByPermission(candidates, permission.getAllowedDocumentIds(), topK);
            filteredChunks = filterResult.allowedChunks();
            blockedDocumentIds = filterResult.blockedDocumentIds();

            if (filteredChunks.isEmpty()) {
                status = STATUS_NO_CONTEXT;
                message = "No permission-safe retrieval context found.";
                answer = "没有检索到当前用户有权限访问且与问题相关的资料，因此无法确认答案。";
                return buildResponse(traceId, status, answer, List.of(), List.of(), blockedDocumentIds, List.of(), topK, startedAt, message);
            }

            List<RagCitation> citations = toCitations(filteredChunks);
            answer = ragLlmClient.generate(buildPrompt(request.getQuestion(), filteredChunks));
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
            answer = "RAG 查询失败：" + e.getMessage();
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
            writeLog(request, permission, username, answer, filteredChunks, blockedDocumentIds, topK, startedAt, status, message);
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

    private String buildPrompt(String question, List<VectorSearchResult> chunks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是企业内部 RAG 问答助手。请严格遵守以下规则：\n");
        prompt.append("1. 只能依据给定资料回答，不要编造资料之外的事实。\n");
        prompt.append("2. 如果资料不足，请明确说明无法确认。\n");
        prompt.append("3. 回答应简洁、可执行，并在相关句子后引用来源编号，例如 [1]。\n\n");
        prompt.append("用户问题：\n").append(question).append("\n\n");
        prompt.append("可用资料：\n");

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

    private List<RagCitation> toCitations(List<VectorSearchResult> chunks) {
        return chunks.stream()
                .map(chunk -> RagCitation.builder()
                        .documentId(chunk.getDocumentId())
                        .chunkId(chunk.getChunkId())
                        .chunkIndex(chunk.getChunkIndex())
                        .score(chunk.getScore())
                        .snippet(snippet(chunk.getChunkText()))
                        .build())
                .collect(Collectors.toList());
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
        log.setTopK(topK);
        log.setLatencyMs(latencyMs(startedAt));
        log.setStatus(status);
        log.setErrorMsg(STATUS_FAIL.equals(status) ? errorMessage : null);
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
