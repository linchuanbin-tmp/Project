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
public class KnowledgeBaseResponse {

    private Long id;

    private String name;

    private String description;

    private String ownerUsername;

    private Long deptId;

    private String visibility;

    private Integer securityLevel;

    private String status;

    private Integer documentCount;

    private Integer chunkCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
