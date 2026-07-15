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
public class SourceDocumentResponse {

    private Long id;

    private Long kbId;

    private Long sysDocumentId;

    private String title;

    private String originalFileName;

    private String fileType;

    private String mimeType;

    private Long fileSize;

    private String storageProvider;

    private String storageBucket;

    private String storageObjectKey;

    private String contentHash;

    private String status;

    private String parserStatus;

    private String indexStatus;

    private Integer chunkCount;

    private Integer securityLevel;

    private Long deptId;

    private String uploadedBy;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
