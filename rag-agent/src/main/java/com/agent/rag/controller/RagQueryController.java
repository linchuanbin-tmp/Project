package com.agent.rag.controller;

import com.agent.rag.dto.RagQueryRequest;
import com.agent.rag.dto.RagQueryResponse;
import com.agent.rag.service.RagQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class RagQueryController {

    private final RagQueryService ragQueryService;

    @PostMapping("/query")
    public RagQueryResponse query(
            @Valid @RequestBody RagQueryRequest request,
            @RequestHeader("X-User-Name") String username,
            @RequestHeader(value = "X-User-Roles", required = false, defaultValue = "") String roles) {
        return ragQueryService.query(request, username, roles);
    }
}
