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
public class RagDocumentIndexStatus {

    private Long documentId;

    private String title;

    private Long deptId;

    private Integer securityLevel;

    private String fileType;

    private String parseStatus;

    private Boolean hasStoredFile;

    private Boolean indexed;

    private Integer chunkCount;

    private LocalDateTime documentCreateTime;

    private LocalDateTime lastIndexedAt;

    private String firstVectorId;

    private String latestContentHash;
}
