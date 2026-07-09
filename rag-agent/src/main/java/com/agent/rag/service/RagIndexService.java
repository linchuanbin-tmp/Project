package com.agent.rag.service;

import com.agent.rag.dto.RagIndexResponse;
import com.agent.rag.entity.RagIndexTask;

import java.util.List;

public interface RagIndexService {

    RagIndexResponse indexDocument(Long documentId);

    RagIndexResponse rebuildAll();

    RagIndexResponse deleteDocumentIndex(Long documentId);

    List<RagIndexTask> listTasks(Integer limit);
}
