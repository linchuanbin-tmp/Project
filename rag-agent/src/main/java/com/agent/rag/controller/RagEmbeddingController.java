package com.agent.rag.controller;

import com.agent.rag.dto.EmbeddingProfile;
import com.agent.rag.dto.EmbeddingProfileActivationResponse;
import com.agent.rag.dto.EmbeddingReadiness;
import com.agent.rag.service.EmbeddingClient;
import com.agent.rag.service.EmbeddingRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/rag/embedding")
@RequiredArgsConstructor
public class RagEmbeddingController {

    private final EmbeddingRuntimeConfigService embeddingConfigService;
    private final EmbeddingClient embeddingClient;

    @GetMapping("/profiles")
    public List<EmbeddingProfile> listProfiles() {
        return embeddingConfigService.listProfiles();
    }

    @GetMapping("/active")
    public EmbeddingProfile getActiveProfile() {
        return embeddingConfigService.getActiveProfile();
    }

    @PostMapping("/profiles/{profileId}/test")
    public EmbeddingReadiness testProfile(@PathVariable String profileId) {
        return embeddingConfigService.withProfile(profileId, embeddingClient::checkReadiness);
    }

    @PostMapping("/profiles/{profileId}/activate")
    public EmbeddingProfileActivationResponse activateProfile(@PathVariable String profileId) {
        return embeddingConfigService.activateProfile(profileId);
    }
}
