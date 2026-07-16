package com.agent.rag.service;

import com.agent.rag.dto.VectorRecord;
import com.agent.rag.dto.VectorSearchResult;

import java.util.List;

public interface VectorStoreService {

    void initializeCollection();

    void upsert(List<VectorRecord> records);

    void deleteByDocumentId(Long documentId);

    List<VectorSearchResult> search(List<Float> queryEmbedding, int topK, List<Long> allowedDocumentIds);
}
