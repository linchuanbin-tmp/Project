package com.agent.rag.controller;

import com.agent.rag.dto.RagIndexResponse;
import com.agent.rag.entity.RagIndexTask;
import com.agent.rag.service.RagIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/rag/index")
@RequiredArgsConstructor
public class RagIndexController {

    private final RagIndexService ragIndexService;

    @PostMapping("/document/{documentId}")
    public RagIndexResponse indexDocument(@PathVariable Long documentId) {
        return ragIndexService.indexDocument(documentId);
    }

    @PostMapping("/rebuild")
    public RagIndexResponse rebuildAll() {
        return ragIndexService.rebuildAll();
    }

    @DeleteMapping("/document/{documentId}")
    public RagIndexResponse deleteDocumentIndex(@PathVariable Long documentId) {
        return ragIndexService.deleteDocumentIndex(documentId);
    }

    @GetMapping("/tasks")
    public List<RagIndexTask> listTasks(@RequestParam(required = false) Integer limit) {
        return ragIndexService.listTasks(limit);
    }
}
