package com.agent.rag.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/rag")
public class RagHealthController {

    @Value("${rag.vector-store.provider:milvus}")
    private String vectorStoreProvider;

    @Value("${rag.embedding.provider:http}")
    private String embeddingProvider;

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "rag-agent",
                "vectorStore", vectorStoreProvider,
                "embeddingProvider", embeddingProvider
        );
    }
}
