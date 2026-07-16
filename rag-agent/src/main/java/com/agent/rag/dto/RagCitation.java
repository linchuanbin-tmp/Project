package com.agent.rag.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RagCitation {

    private Long documentId;

    private String documentTitle;

    private Long chunkId;

    private Integer chunkIndex;

    private Double score;

    private String snippet;
}
