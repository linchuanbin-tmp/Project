package com.agent.rag.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RagQueryResponse {

    private String traceId;

    private String status;

    private String answer;

    private List<RagCitation> citations;

    private List<VectorSearchResult> chunks;

    private List<Long> retrievedDocumentIds;

    private List<Long> blockedDocumentIds;

    private Integer topK;

    private Integer latencyMs;

    private String message;
}
