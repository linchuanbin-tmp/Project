package com.agent.rag.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EmbeddingRuntimeConfig {

    String profile;
    String provider;
    String endpoint;
    String apiKey;
    String model;
    int dimension;
    int timeoutMs;
    String collectionName;
}
