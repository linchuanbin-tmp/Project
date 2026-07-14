package com.agent.rag.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RagAccessRequest {

    @NotNull(message = "documentId must not be null")
    private Long documentId;

    private String reason;
}
