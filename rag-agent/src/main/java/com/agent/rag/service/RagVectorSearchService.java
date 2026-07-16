package com.agent.rag.service;

import com.agent.rag.dto.VectorSearchResult;

import java.util.List;

public interface RagVectorSearchService {

    List<VectorSearchResult> search(String question, Integer topK, List<Long> allowedDocumentIds);
}
