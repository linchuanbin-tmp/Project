package com.agent.rag.controller;

import com.agent.rag.dto.RagPermissionSnapshot;
import com.agent.rag.service.RagPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rag/permissions")
@RequiredArgsConstructor
public class RagPermissionController {

    private final RagPermissionService ragPermissionService;

    @GetMapping("/documents")
    public RagPermissionSnapshot getAccessibleDocuments(
            @RequestHeader("X-User-Name") String username,
            @RequestHeader(value = "X-User-Roles", required = false, defaultValue = "") String roles) {
        return ragPermissionService.resolveAccessibleDocuments(username, roles);
    }
}
