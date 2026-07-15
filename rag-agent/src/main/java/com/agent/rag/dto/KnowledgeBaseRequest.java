package com.agent.rag.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KnowledgeBaseRequest {

    @NotBlank(message = "Knowledge base name is required")
    @Size(max = 120, message = "Knowledge base name must be no more than 120 characters")
    private String name;

    @Size(max = 500, message = "Description must be no more than 500 characters")
    private String description;

    private Long deptId;

    private String visibility;

    @Min(value = 1, message = "Security level must be between 1 and 3")
    @Max(value = 3, message = "Security level must be between 1 and 3")
    private Integer securityLevel;
}
