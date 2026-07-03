package com.agent.user.service;

import com.agent.user.dto.DocumentResponse;
import com.agent.user.entity.SysDocument;
import java.util.List;

public interface SysDocumentService {
    List<DocumentResponse> listDocumentsForUser(Long userId);
    void createDocument(SysDocument document, Long creatorId);
    void updateDocument(SysDocument document, Long updaterId);
    void deleteDocument(Long id, Long deleterId);
}
