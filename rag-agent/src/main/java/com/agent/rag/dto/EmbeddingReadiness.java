package com.agent.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingReadiness {

    private String provider;

    private Boolean ready;

    private Boolean probed;

    private String message;

    private Integer dimension;

    private Integer actualDimension;

    private String model;

    private Boolean endpointConfigured;

    private Boolean apiKeyConfigured;

    private Integer timeoutMs;
}
