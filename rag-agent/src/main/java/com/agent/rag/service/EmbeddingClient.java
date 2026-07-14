package com.agent.rag.service;

import com.agent.rag.dto.EmbeddingReadiness;

import java.util.List;

public interface EmbeddingClient {

    List<Float> embed(String text);

    EmbeddingReadiness checkReadiness();
}
