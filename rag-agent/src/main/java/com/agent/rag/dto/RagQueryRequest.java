package com.agent.rag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RagQueryRequest {

    @NotBlank(message = "question must not be empty")
    private String question;

    private Integer topK;
}
