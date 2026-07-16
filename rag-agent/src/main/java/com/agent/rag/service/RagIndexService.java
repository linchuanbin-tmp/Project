package com.agent.rag.service;

import com.agent.rag.dto.RagIndexResponse;
import com.agent.rag.dto.RagDocumentChunkDto;
import com.agent.rag.dto.RagDocumentIndexStatus;
import com.agent.rag.entity.RagIndexTask;

import java.util.List;

public interface RagIndexService {

    RagIndexResponse indexDocument(Long documentId);

    RagIndexResponse reprocessDocument(Long documentId);

    RagIndexResponse rebuildAll();

    RagIndexResponse deleteDocumentIndex(Long documentId);

    RagIndexResponse getTask(Long taskId);

    List<RagIndexTask> listTasks(Integer limit);

    List<RagDocumentIndexStatus> listDocumentIndexStatus();

    List<RagDocumentChunkDto> listDocumentChunks(Long documentId);
}
