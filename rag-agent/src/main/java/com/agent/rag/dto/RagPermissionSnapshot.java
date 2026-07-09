package com.agent.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagPermissionSnapshot {

    private Long userId;

    private String username;

    private Long deptId;

    private Integer clearanceLevel;

    private List<String> roles;

    private List<Long> allowedDocumentIds;

    private List<AccessibleDocumentDto> accessibleDocuments;
}
