package com.agent.rag.service;

import com.agent.rag.dto.RagPermissionSnapshot;

public interface RagPermissionService {

    RagPermissionSnapshot resolveAccessibleDocuments(String username, String rolesHeader);
}
