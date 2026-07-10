package com.agent.rag.service;

import com.agent.rag.dto.RagQueryRequest;
import com.agent.rag.dto.RagQueryResponse;

public interface RagQueryService {

    RagQueryResponse query(RagQueryRequest request, String username, String rolesHeader);
}
