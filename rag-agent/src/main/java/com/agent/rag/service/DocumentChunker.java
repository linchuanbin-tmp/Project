package com.agent.rag.service;

import com.agent.rag.dto.DocumentChunk;

import java.util.List;

public interface DocumentChunker {

    List<DocumentChunk> split(String content);
}
