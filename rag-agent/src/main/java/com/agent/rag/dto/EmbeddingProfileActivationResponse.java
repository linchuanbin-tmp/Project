package com.agent.rag.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EmbeddingProfileActivationResponse {

    String activeProfileId;
    String previousProfileId;
    String indexStatus;
    Boolean rebuildRequired;
    String message;
}
