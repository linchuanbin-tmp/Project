package com.agent.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagDocumentChunkDto {

    private Long chunkId;

    private Long documentId;

    private Integer chunkIndex;

    private String chunkText;

    private Integer tokenCount;

    private String vectorId;

    private Integer securityLevel;

    private Long deptId;

    private String contentHash;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
