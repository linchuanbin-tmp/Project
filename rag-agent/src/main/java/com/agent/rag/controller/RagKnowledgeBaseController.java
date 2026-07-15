package com.agent.rag.controller;

import com.agent.rag.dto.DocumentUploadResponse;
import com.agent.rag.dto.KnowledgeBaseRequest;
import com.agent.rag.dto.KnowledgeBaseResponse;
import com.agent.rag.dto.SourceDocumentResponse;
import com.agent.rag.service.RagKnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/rag/kb")
@RequiredArgsConstructor
public class RagKnowledgeBaseController {

    private final RagKnowledgeBaseService knowledgeBaseService;

    @GetMapping
    public List<KnowledgeBaseResponse> listKnowledgeBases() {
        return knowledgeBaseService.listKnowledgeBases();
    }

    @PostMapping
    public KnowledgeBaseResponse createKnowledgeBase(
            @Valid @RequestBody KnowledgeBaseRequest request,
            @RequestHeader(value = "X-User-Name", required = false) String username
    ) {
        return knowledgeBaseService.createKnowledgeBase(request, usernameOrDefault(username));
    }

    @PutMapping("/{kbId}")
    public KnowledgeBaseResponse updateKnowledgeBase(
            @PathVariable Long kbId,
            @Valid @RequestBody KnowledgeBaseRequest request
    ) {
        return knowledgeBaseService.updateKnowledgeBase(kbId, request);
    }

    @DeleteMapping("/{kbId}")
    public void deleteKnowledgeBase(@PathVariable Long kbId) {
        knowledgeBaseService.deleteKnowledgeBase(kbId);
    }

    @GetMapping("/{kbId}/documents")
    public List<SourceDocumentResponse> listDocuments(@PathVariable Long kbId) {
        return knowledgeBaseService.listDocuments(kbId);
    }

    @PostMapping("/{kbId}/documents/upload")
    public DocumentUploadResponse uploadDocuments(
            @PathVariable Long kbId,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "deptId", required = false) Long deptId,
            @RequestParam(value = "securityLevel", required = false) Integer securityLevel,
            @RequestHeader(value = "X-User-Name", required = false) String username
    ) {
        return knowledgeBaseService.uploadDocuments(
                kbId,
                files,
                deptId,
                securityLevel,
                usernameOrDefault(username)
        );
    }

    @PostMapping("/{kbId}/documents/{documentId}/reprocess")
    public SourceDocumentResponse reprocessDocument(@PathVariable Long kbId, @PathVariable Long documentId) {
        return knowledgeBaseService.reprocessDocument(kbId, documentId);
    }

    @DeleteMapping("/{kbId}/documents/{documentId}")
    public void deleteDocument(@PathVariable Long kbId, @PathVariable Long documentId) {
        knowledgeBaseService.deleteDocument(kbId, documentId);
    }

    private String usernameOrDefault(String username) {
        return username == null || username.isBlank() ? "system" : username;
    }
}
