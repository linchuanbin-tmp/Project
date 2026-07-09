package com.agent.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagIndexResponse {

    private Long taskId;

    private Long documentId;

    private String taskType;

    private String status;

    private Integer chunkCount;

    private String message;
}
