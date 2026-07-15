package com.agent.rag.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EmbeddingProfile {

    String id;
    String label;
    String provider;
    String endpoint;
    Boolean endpointConfigured;
    Boolean apiKeyConfigured;
    String model;
    int dimension;
    int timeoutMs;
    String collectionName;
    Boolean active;
    String indexStatus;
    String indexMessage;
}
