package com.agent.rag.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VectorSearchResult {

    private String vectorId;

    private Long documentId;

    private Long chunkId;

    private Integer chunkIndex;

    private Long deptId;

    private Integer securityLevel;

    private Double score;

    private String chunkText;
}
