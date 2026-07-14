package com.agent.rag.controller;

import com.agent.rag.dto.RagAccessRequest;
import com.agent.rag.dto.RagAccessRequestResponse;
import com.agent.rag.service.RagAccessRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rag/access-request")
@RequiredArgsConstructor
public class RagAccessRequestController {

    private final RagAccessRequestService ragAccessRequestService;

    @PostMapping
    public RagAccessRequestResponse requestAccess(
            @Valid @RequestBody RagAccessRequest request,
            @RequestHeader("X-User-Name") String username,
            @RequestHeader(value = "X-User-Roles", required = false, defaultValue = "") String roles) {
        return ragAccessRequestService.requestAccess(request, username, roles);
    }
}
