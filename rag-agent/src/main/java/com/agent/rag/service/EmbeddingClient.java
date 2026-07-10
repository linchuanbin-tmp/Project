package com.agent.rag.service;

import java.util.List;

public interface EmbeddingClient {

    List<Float> embed(String text);
}
