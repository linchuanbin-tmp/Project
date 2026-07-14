package com.agent.rag.service;

import com.agent.rag.dto.RagAccessRequest;
import com.agent.rag.dto.RagAccessRequestResponse;

public interface RagAccessRequestService {

    RagAccessRequestResponse requestAccess(RagAccessRequest request, String username, String rolesHeader);
}
