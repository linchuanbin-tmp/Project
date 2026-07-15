package com.agent.rag.service;

import com.agent.rag.dto.DocumentUploadResponse;
import com.agent.rag.dto.KnowledgeBaseRequest;
import com.agent.rag.dto.KnowledgeBaseResponse;
import com.agent.rag.dto.SourceDocumentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface RagKnowledgeBaseService {

    List<KnowledgeBaseResponse> listKnowledgeBases();

    KnowledgeBaseResponse createKnowledgeBase(KnowledgeBaseRequest request, String username);

    KnowledgeBaseResponse updateKnowledgeBase(Long kbId, KnowledgeBaseRequest request);

    void deleteKnowledgeBase(Long kbId);

    List<SourceDocumentResponse> listDocuments(Long kbId);

    DocumentUploadResponse uploadDocuments(
            Long kbId,
            MultipartFile[] files,
            Long deptId,
            Integer securityLevel,
            String username
    );

    void deleteDocument(Long kbId, Long documentId);
}
