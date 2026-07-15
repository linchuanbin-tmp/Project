package com.agent.user.service;

import com.agent.user.dto.DocumentResponse;
import com.agent.user.entity.SysDocument;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SysDocumentService {
    List<DocumentResponse> listDocumentsForUser(Long userId);
    void createDocument(SysDocument document, Long creatorId);
    void updateDocument(SysDocument document, Long updaterId);
    void deleteDocument(Long id, Long deleterId);
    SysDocument getDocumentById(Long id);
    void createDocumentFromFile(String title, MultipartFile file, Integer securityLevel, Long deptId, Long creatorId);
}
