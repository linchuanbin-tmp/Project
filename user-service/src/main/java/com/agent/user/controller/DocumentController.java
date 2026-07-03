package com.agent.user.controller;

import com.agent.user.dto.DocumentResponse;
import com.agent.user.dto.Result;
import com.agent.user.entity.User;
import com.agent.user.service.SysDocumentService;
import com.agent.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/user/document")
@RequiredArgsConstructor
public class DocumentController {

    private final SysDocumentService documentService;
    private final UserService userService;

    @GetMapping("/list")
    public Result<List<DocumentResponse>> listDocuments() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if ("anonymousUser".equals(username)) {
            return Result.error(401, "Not authenticated");
        }

        User user = userService.getUserByUsername(username);
        if (user == null) {
            return Result.error(404, "User not found");
        }

        List<DocumentResponse> docs = documentService.listDocumentsForUser(user.getId());
        return Result.success(docs);
    }
}
