package com.agent.user.controller;

import com.agent.user.dto.DocumentResponse;
import com.agent.user.dto.Result;
import com.agent.user.entity.SysDocument;
import com.agent.user.entity.User;
import com.agent.user.service.MinioStorageService;
import com.agent.user.service.SysDocumentService;
import com.agent.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/user/document")
@RequiredArgsConstructor
public class DocumentController {

    private final SysDocumentService documentService;
    private final UserService userService;
    private final MinioStorageService minioStorageService;
    private final SystemConfigController systemConfigController;

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

    @PostMapping("/upload")
    public Result<String> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam("securityLevel") Integer securityLevel,
            @RequestParam(value = "deptId", required = false) Long deptId) {
        try {
            long maxMb = systemConfigController.resolveMaxUploadSize();
            long maxBytes = maxMb * 1024 * 1024;
            if (file.getSize() > maxBytes) {
                return Result.error("File size exceeds the maximum allowed (" + maxMb + " MB).");
            }
            Long userId = getCurrentUserId();
            String docTitle = (title != null && !title.isBlank())
                    ? title
                    : file.getOriginalFilename();
            documentService.createDocumentFromFile(docTitle, file, securityLevel, deptId, userId);
            return Result.success("Document uploaded successfully");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/download/{id}")
    public void downloadDocument(@PathVariable Long id, HttpServletResponse response) {
        try {
            Long userId = getCurrentUserId();
            List<DocumentResponse> docs = documentService.listDocumentsForUser(userId);
            DocumentResponse doc = docs.stream()
                    .filter(d -> d.getId().equals(id))
                    .findFirst()
                    .orElse(null);

            if (doc == null || !Boolean.TRUE.equals(doc.getAccessible())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
                return;
            }

            SysDocument entity = documentService.getDocumentById(id);
            if (entity == null || entity.getMinioObjectKey() == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
                return;
            }

            String filename = entity.getTitle();
            String ext = "";
            String originalFilename = entity.getMinioObjectKey();
            int dotIdx = originalFilename.lastIndexOf(".");
            if (dotIdx >= 0) ext = originalFilename.substring(dotIdx);
            if (!filename.contains(".")) filename = filename + ext;

            boolean isPdf = entity.getFileType() != null && entity.getFileType().equals("PDF");
            response.setContentType(minioStorageService.getContentType(entity.getMinioObjectKey()));
            response.setHeader("Content-Disposition",
                    (isPdf ? "inline" : "attachment") + "; filename=\"" + URLEncoder.encode(filename, StandardCharsets.UTF_8) + "\"");

            try (InputStream is = minioStorageService.download(entity.getMinioObjectKey());
                 OutputStream os = response.getOutputStream()) {
                StreamUtils.copy(is, os);
                os.flush();
            }
        } catch (Exception e) {
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            } catch (Exception ignored) {}
        }
    }
}
