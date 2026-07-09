package com.agent.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessibleDocumentDto {

    private Long documentId;

    private String title;

    private Long deptId;

    private Integer securityLevel;

    private String accessReason;
}
