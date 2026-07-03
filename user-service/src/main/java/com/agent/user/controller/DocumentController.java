package com.agent.user.controller;

import com.agent.user.dto.DocumentResponse;
import com.agent.user.dto.Result;
import com.agent.user.entity.SysDocument;
import com.agent.user.entity.User;
import com.agent.user.service.SysDocumentService;
import com.agent.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/document")
@RequiredArgsConstructor
public class DocumentController {

    private final SysDocumentService documentService;
    private final UserService userService;

    private Long getCurrentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if ("anonymousUser".equals(username)) {
            throw new RuntimeException("Not authenticated");
        }
        User user = userService.getUserByUsername(username);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        return user.getId();
    }

    @GetMapping("/list")
    public Result<List<DocumentResponse>> listDocuments() {
        try {
            Long userId = getCurrentUserId();
            List<DocumentResponse> docs = documentService.listDocumentsForUser(userId);
            return Result.success(docs);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/create")
    public Result<String> createDocument(@RequestBody SysDocument document) {
        try {
            Long userId = getCurrentUserId();
            documentService.createDocument(document, userId);
            return Result.success("Document created successfully");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/update")
    public Result<String> updateDocument(@RequestBody SysDocument document) {
        try {
            Long userId = getCurrentUserId();
            documentService.updateDocument(document, userId);
            return Result.success("Document updated successfully");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public Result<String> deleteDocument(@PathVariable Long id) {
        try {
            Long userId = getCurrentUserId();
            documentService.deleteDocument(id, userId);
            return Result.success("Document deleted successfully");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
