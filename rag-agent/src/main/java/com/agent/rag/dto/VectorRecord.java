package com.agent.rag.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class VectorRecord {

    private String vectorId;

    private Long documentId;

    private Long chunkId;

    private Integer chunkIndex;

    private Long deptId;

    private Integer securityLevel;

    private String contentHash;

    private String chunkText;

    private List<Float> embedding;
}
