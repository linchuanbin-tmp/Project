package com.agent.rag.service.impl;

import com.agent.rag.dto.DocumentChunk;
import com.agent.rag.dto.ParsedDocument;
import com.agent.rag.dto.RagDocumentChunkDto;
import com.agent.rag.dto.RagDocumentIndexStatus;
import com.agent.rag.dto.RagIndexResponse;
import com.agent.rag.dto.VectorRecord;
import com.agent.rag.entity.RagDocumentChunk;
import com.agent.rag.entity.RagIndexTask;
import com.agent.rag.entity.SysDocument;
import com.agent.rag.mapper.RagDocumentChunkMapper;
import com.agent.rag.mapper.RagIndexTaskMapper;
import com.agent.rag.mapper.SysDocumentMapper;
import com.agent.rag.service.DocumentChunker;
import com.agent.rag.service.DocumentParserService;
import com.agent.rag.service.DocumentStorageService;
import com.agent.rag.service.EmbeddingClient;
import com.agent.rag.service.RagIndexService;
import com.agent.rag.service.VectorStoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RagIndexServiceImpl implements RagIndexService {

    private static final String TASK_INDEX_DOCUMENT = "INDEX_DOCUMENT";
    private static final String TASK_REPROCESS_DOCUMENT = "REPROCESS_DOCUMENT";
    private static final String TASK_REBUILD_ALL = "REBUILD_ALL";
    private static final String TASK_DELETE_DOCUMENT = "DELETE_DOCUMENT";

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAIL = "FAIL";
    private static final String PARSE_STATUS_PENDING = "PENDING";
    private static final String PARSE_STATUS_DONE = "DONE";
    private static final String PARSE_STATUS_FAILED = "FAILED";

    private final SysDocumentMapper sysDocumentMapper;
    private final RagDocumentChunkMapper ragDocumentChunkMapper;
    private final RagIndexTaskMapper ragIndexTaskMapper;
    private final DocumentChunker documentChunker;
    private final DocumentParserService documentParserService;
    private final DocumentStorageService documentStorageService;
    private final EmbeddingClient embeddingClient;
    private final VectorStoreService vectorStoreService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RagIndexResponse indexDocument(Long documentId) {
        return processDocument(documentId, TASK_INDEX_DOCUMENT, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RagIndexResponse reprocessDocument(Long documentId) {
        return processDocument(documentId, TASK_REPROCESS_DOCUMENT, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RagIndexResponse rebuildAll() {
        RagIndexTask task = createTask(null, TASK_REBUILD_ALL);
        try {
            List<SysDocument> documents = sysDocumentMapper.selectList(null);
            int totalChunks = 0;
            for (SysDocument document : documents) {
                totalChunks += writeChunks(document);
            }
            markTask(task, STATUS_SUCCESS, "Rebuilt " + documents.size() + " document(s), " + totalChunks + " chunk(s).");
            return response(task, totalChunks);
        } catch (Exception e) {
            markTask(task, STATUS_FAIL, e.getMessage());
            return response(task, 0);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RagIndexResponse deleteDocumentIndex(Long documentId) {
        RagIndexTask task = createTask(documentId, TASK_DELETE_DOCUMENT);
        try {
            vectorStoreService.deleteByDocumentId(documentId);
            int deleted = ragDocumentChunkMapper.hardDeleteByDocumentId(documentId);
            markTask(task, STATUS_SUCCESS, "Deleted " + deleted + " chunk(s).");
            return response(task, deleted);
        } catch (Exception e) {
            markTask(task, STATUS_FAIL, e.getMessage());
            return response(task, 0);
        }
    }

    @Override
    public List<RagIndexTask> listTasks(Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 20 : Math.min(limit, 100);
        return ragIndexTaskMapper.selectList(
                new LambdaQueryWrapper<RagIndexTask>()
                        .orderByDesc(RagIndexTask::getCreateTime)
                        .last("LIMIT " + safeLimit)
        );
    }

    @Override
    public List<RagDocumentIndexStatus> listDocumentIndexStatus() {
        List<SysDocument> documents = sysDocumentMapper.selectList(
                new LambdaQueryWrapper<SysDocument>()
                        .orderByAsc(SysDocument::getId)
        );
        List<RagDocumentChunk> chunks = ragDocumentChunkMapper.selectList(
                new LambdaQueryWrapper<RagDocumentChunk>()
                        .orderByAsc(RagDocumentChunk::getDocumentId)
                        .orderByAsc(RagDocumentChunk::getChunkIndex)
        );
        Map<Long, List<RagDocumentChunk>> chunksByDocumentId = chunks.stream()
                .collect(Collectors.groupingBy(
                        RagDocumentChunk::getDocumentId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<RagDocumentIndexStatus> result = new ArrayList<>();
        for (SysDocument document : documents) {
            List<RagDocumentChunk> documentChunks = chunksByDocumentId.getOrDefault(document.getId(), List.of());
            RagDocumentChunk latestChunk = documentChunks.stream()
                    .max(Comparator.comparing(RagDocumentChunk::getUpdateTime, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(null);
            RagDocumentChunk firstChunk = documentChunks.stream()
                    .min(Comparator.comparing(RagDocumentChunk::getChunkIndex, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);

            result.add(RagDocumentIndexStatus.builder()
                    .documentId(document.getId())
                    .title(document.getTitle())
                    .deptId(document.getDeptId())
                    .securityLevel(document.getSecurityLevel())
                    .fileType(document.getFileType())
                    .parseStatus(document.getParseStatus())
                    .hasStoredFile(document.getMinioObjectKey() != null && !document.getMinioObjectKey().isBlank())
                    .indexed(!documentChunks.isEmpty())
                    .chunkCount(documentChunks.size())
                    .documentCreateTime(document.getCreateTime())
                    .lastIndexedAt(latestChunk != null ? latestChunk.getUpdateTime() : null)
                    .firstVectorId(firstChunk != null ? firstChunk.getVectorId() : null)
                    .latestContentHash(latestChunk != null ? latestChunk.getContentHash() : null)
                    .build());
        }
        return result;
    }

    @Override
    public List<RagDocumentChunkDto> listDocumentChunks(Long documentId) {
        return ragDocumentChunkMapper.selectList(
                        new LambdaQueryWrapper<RagDocumentChunk>()
                                .eq(RagDocumentChunk::getDocumentId, documentId)
                                .orderByAsc(RagDocumentChunk::getChunkIndex)
                )
                .stream()
                .map(this::toChunkDto)
                .toList();
    }

    private RagIndexResponse processDocument(Long documentId, String taskType, boolean forceParse) {
        RagIndexTask task = createTask(documentId, taskType);
        try {
            SysDocument document = sysDocumentMapper.selectById(documentId);
            if (document == null) {
                throw new RuntimeException("Document not found: " + documentId);
            }

            int chunkCount = writeChunks(document, forceParse);
            markTask(task, STATUS_SUCCESS, "Indexed " + chunkCount + " chunk(s).");
            return response(task, chunkCount);
        } catch (Exception e) {
            markTask(task, STATUS_FAIL, e.getMessage());
            return response(task, 0);
        }
    }

    private int writeChunks(SysDocument document) {
        return writeChunks(document, false);
    }

    private int writeChunks(SysDocument document, boolean forceParse) {
        document = ensureDocumentTextReady(document, forceParse);
        vectorStoreService.deleteByDocumentId(document.getId());
        ragDocumentChunkMapper.hardDeleteByDocumentId(document.getId());
        List<DocumentChunk> chunks = documentChunker.split(document.getContent());
        List<VectorRecord> vectorRecords = new java.util.ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            RagDocumentChunk entity = new RagDocumentChunk();
            entity.setDocumentId(document.getId());
            entity.setChunkIndex(chunk.getChunkIndex());
            entity.setChunkText(chunk.getText());
            entity.setTokenCount(chunk.getTokenCount());
            entity.setVectorId(buildVectorId(document.getId(), chunk.getChunkIndex(), chunk.getContentHash()));
            entity.setSecurityLevel(document.getSecurityLevel() != null ? document.getSecurityLevel() : 1);
            entity.setDeptId(document.getDeptId());
            entity.setContentHash(chunk.getContentHash());
            entity.setDeleted(0);
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateTime(LocalDateTime.now());
            ragDocumentChunkMapper.insert(entity);

            List<Float> embedding;
            try {
                embedding = embeddingClient.embed(entity.getChunkText());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to generate embedding for document "
                        + document.getId() + " chunk " + chunk.getChunkIndex()
                        + ": " + e.getMessage(), e);
            }

            vectorRecords.add(VectorRecord.builder()
                    .vectorId(entity.getVectorId())
                    .documentId(entity.getDocumentId())
                    .chunkId(entity.getId())
                    .chunkIndex(entity.getChunkIndex())
                    .deptId(entity.getDeptId())
                    .securityLevel(entity.getSecurityLevel())
                    .contentHash(entity.getContentHash())
                    .chunkText(entity.getChunkText())
                    .embedding(embedding)
                    .build());
        }
        vectorStoreService.upsert(vectorRecords);
        return chunks.size();
    }

    private SysDocument ensureDocumentTextReady(SysDocument document, boolean forceParse) {
        String content = document.getContent();
        boolean hasText = content != null && !content.isBlank();
        boolean hasStoredFile = document.getMinioObjectKey() != null && !document.getMinioObjectKey().isBlank();
        boolean parsePending = PARSE_STATUS_PENDING.equalsIgnoreCase(document.getParseStatus())
                || PARSE_STATUS_FAILED.equalsIgnoreCase(document.getParseStatus());

        if (!forceParse && hasText && !parsePending) {
            return document;
        }
        if (!hasStoredFile) {
            if (hasText) {
                return document;
            }
            throw new IllegalStateException("Document " + document.getId() + " has no text content to index.");
        }

        ParsedDocument parsedDocument = documentParserService.parse(
                document.getTitle(),
                mimeTypeFor(document.getFileType()),
                documentStorageService.readOriginal(document.getMinioObjectKey())
        );
        if (!parsedDocument.isParsed()) {
            document.setParseStatus(PARSE_STATUS_FAILED);
            sysDocumentMapper.updateById(document);
            throw new IllegalStateException("Failed to parse document " + document.getId()
                    + ": " + parsedDocument.getErrorMessage());
        }

        document.setContent(parsedDocument.getText());
        document.setParseStatus(PARSE_STATUS_DONE);
        sysDocumentMapper.updateById(document);
        return document;
    }

    private String mimeTypeFor(String fileType) {
        if (fileType == null) {
            return null;
        }
        return switch (fileType.toUpperCase()) {
            case "PDF" -> "application/pdf";
            case "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "PPT", "PPTX" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "MARKDOWN" -> "text/markdown";
            default -> null;
        };
    }

    private String buildVectorId(Long documentId, Integer chunkIndex, String contentHash) {
        String hashPrefix = contentHash != null && contentHash.length() >= 12
                ? contentHash.substring(0, 12)
                : "nohash";
        return "doc_" + documentId + "_chunk_" + chunkIndex + "_" + hashPrefix;
    }

    private RagIndexTask createTask(Long documentId, String taskType) {
        RagIndexTask task = new RagIndexTask();
        task.setDocumentId(documentId);
        task.setTaskType(taskType);
        task.setStatus(STATUS_RUNNING);
        task.setMessage("Started");
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        ragIndexTaskMapper.insert(task);
        return task;
    }

    private void markTask(RagIndexTask task, String status, String message) {
        task.setStatus(status);
        task.setMessage(message);
        task.setUpdateTime(LocalDateTime.now());
        ragIndexTaskMapper.updateById(task);
    }

    private RagIndexResponse response(RagIndexTask task, Integer chunkCount) {
        return RagIndexResponse.builder()
                .taskId(task.getId())
                .documentId(task.getDocumentId())
                .taskType(task.getTaskType())
                .status(task.getStatus())
                .chunkCount(chunkCount)
                .message(task.getMessage())
                .build();
    }

    private RagDocumentChunkDto toChunkDto(RagDocumentChunk chunk) {
        return RagDocumentChunkDto.builder()
                .chunkId(chunk.getId())
                .documentId(chunk.getDocumentId())
                .chunkIndex(chunk.getChunkIndex())
                .chunkText(chunk.getChunkText())
                .tokenCount(chunk.getTokenCount())
                .vectorId(chunk.getVectorId())
                .securityLevel(chunk.getSecurityLevel())
                .deptId(chunk.getDeptId())
                .contentHash(chunk.getContentHash())
                .createTime(chunk.getCreateTime())
                .updateTime(chunk.getUpdateTime())
                .build();
    }
}
