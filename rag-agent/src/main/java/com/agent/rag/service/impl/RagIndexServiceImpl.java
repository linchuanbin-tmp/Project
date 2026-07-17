package com.agent.rag.service.impl;

import com.agent.rag.dto.DocumentChunk;
import com.agent.rag.dto.ParsedDocument;
import com.agent.rag.dto.RagDocumentChunkDto;
import com.agent.rag.dto.RagDocumentIndexStatus;
import com.agent.rag.dto.RagIndexResponse;
import com.agent.rag.dto.VectorRecord;
import com.agent.rag.dto.EmbeddingRuntimeConfig;
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
import com.agent.rag.service.EmbeddingRuntimeConfigService;
import com.agent.rag.service.RagIndexService;
import com.agent.rag.service.VectorStoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
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
    private static final String STATUS_QUEUED = "QUEUED";
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
    private final EmbeddingRuntimeConfigService embeddingConfigService;

    @Autowired
    @Qualifier("ragIndexTaskExecutor")
    private Executor ragIndexTaskExecutor;

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
    public RagIndexResponse rebuildAll() {
        RagIndexTask task = createTask(null, TASK_REBUILD_ALL, STATUS_QUEUED, "Index rebuild is starting -- this should finish shortly.");
        ragIndexTaskExecutor.execute(() -> runRebuildAll(task.getId()));
        return response(task, 0);
    }

    private void runRebuildAll(Long taskId) {
        RagIndexTask task = ragIndexTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        try {
            markTask(task, STATUS_RUNNING, "Rebuilding active embedding profile index.");
            embeddingConfigService.markActiveIndexRebuilding();
            int totalChunks = embeddingConfigService.withCurrentProfile(() -> {
                List<SysDocument> documents = sysDocumentMapper.selectList(null);
                int chunks = 0;
                for (SysDocument document : documents) {
                    chunks += writeChunks(document);
                }
                markTask(task, STATUS_SUCCESS, "Rebuilt " + documents.size() + " document(s), " + chunks + " chunk(s).");
                return chunks;
            });
            embeddingConfigService.markActiveIndexReady();
        } catch (Exception e) {
            embeddingConfigService.markActiveIndexFailed(e.getMessage());
            markTask(task, STATUS_FAIL, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RagIndexResponse deleteDocumentIndex(Long documentId) {
        RagIndexTask task = createTask(documentId, TASK_DELETE_DOCUMENT);
        try {
            int deleted = embeddingConfigService.withCurrentProfile(() -> {
                EmbeddingRuntimeConfig config = embeddingConfigService.getCurrentConfig();
                vectorStoreService.deleteByDocumentId(documentId);
                return ragDocumentChunkMapper.hardDeleteByDocumentIdAndProfile(documentId, config.getProfile());
            });
            markTask(task, STATUS_SUCCESS, "Deleted " + deleted + " chunk(s).");
            return response(task, deleted);
        } catch (Exception e) {
            markTask(task, STATUS_FAIL, e.getMessage());
            return response(task, 0);
        }
    }

    @Override
    public RagIndexResponse getTask(Long taskId) {
        RagIndexTask task = ragIndexTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("RAG index task not found: " + taskId);
        }
        return response(task, null);
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
        EmbeddingRuntimeConfig config = embeddingConfigService.getCurrentConfig();
        List<SysDocument> documents = sysDocumentMapper.selectList(
                new LambdaQueryWrapper<SysDocument>()
                        .orderByAsc(SysDocument::getId)
        );
        List<RagDocumentChunk> chunks = ragDocumentChunkMapper.selectList(
                new LambdaQueryWrapper<RagDocumentChunk>()
                        .eq(RagDocumentChunk::getEmbeddingProfile, config.getProfile())
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
                    .embeddingProfile(config.getProfile())
                    .embeddingModel(config.getModel())
                    .vectorCollection(config.getCollectionName())
                    .indexStatus(embeddingConfigService.getActiveIndexStatus())
                    .pipelineStatus(resolvePipelineStatus(document, documentChunks))
                    .pipelineMessage(resolvePipelineMessage(document, documentChunks))
                    .build());
        }
        return result;
    }

    @Override
    public List<RagDocumentChunkDto> listDocumentChunks(Long documentId) {
        EmbeddingRuntimeConfig config = embeddingConfigService.getCurrentConfig();
        return ragDocumentChunkMapper.selectList(
                        new LambdaQueryWrapper<RagDocumentChunk>()
                                .eq(RagDocumentChunk::getDocumentId, documentId)
                                .eq(RagDocumentChunk::getEmbeddingProfile, config.getProfile())
                                .orderByAsc(RagDocumentChunk::getChunkIndex)
                )
                .stream()
                .map(this::toChunkDto)
                .toList();
    }

    private RagIndexResponse processDocument(Long documentId, String taskType, boolean forceParse) {
        RagIndexTask task = createTask(documentId, taskType);
        try {
            int chunkCount = embeddingConfigService.withCurrentProfile(() -> {
                SysDocument document = sysDocumentMapper.selectById(documentId);
                if (document == null) {
                    throw new RuntimeException("Document not found: " + documentId);
                }
                return writeChunks(document, forceParse);
            });
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
        EmbeddingRuntimeConfig config = embeddingConfigService.getCurrentConfig();
        document = ensureDocumentTextReady(document, forceParse);
        vectorStoreService.deleteByDocumentId(document.getId());
        ragDocumentChunkMapper.hardDeleteByDocumentIdAndProfile(document.getId(), config.getProfile());
        List<DocumentChunk> chunks = documentChunker.split(document.getContent());
        List<VectorRecord> vectorRecords = new java.util.ArrayList<>();
        List<RagDocumentChunk> insertedChunks = new java.util.ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            RagDocumentChunk entity = new RagDocumentChunk();
            entity.setDocumentId(document.getId());
            entity.setChunkIndex(chunk.getChunkIndex());
            entity.setChunkText(chunk.getText());
            entity.setTokenCount(chunk.getTokenCount());
            entity.setVectorId(buildVectorId(config.getProfile(), document.getId(), chunk.getChunkIndex(), chunk.getContentHash()));
            entity.setEmbeddingProfile(config.getProfile());
            entity.setEmbeddingModel(config.getModel());
            entity.setVectorCollection(config.getCollectionName());
            entity.setIndexStatus(STATUS_RUNNING);
            entity.setSecurityLevel(document.getSecurityLevel() != null ? document.getSecurityLevel() : 1);
            entity.setDeptId(document.getDeptId());
            entity.setContentHash(chunk.getContentHash());
            entity.setDeleted(0);
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateTime(LocalDateTime.now());
            ragDocumentChunkMapper.insert(entity);
            insertedChunks.add(entity);

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
        for (RagDocumentChunk chunk : insertedChunks) {
            chunk.setIndexStatus(STATUS_SUCCESS);
            ragDocumentChunkMapper.updateById(chunk);
        }
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

    private String buildVectorId(String profile, Long documentId, Integer chunkIndex, String contentHash) {
        String hashPrefix = contentHash != null && contentHash.length() >= 12
                ? contentHash.substring(0, 12)
                : "nohash";
        return profile + "_doc_" + documentId + "_chunk_" + chunkIndex + "_" + hashPrefix;
    }

    private RagIndexTask createTask(Long documentId, String taskType) {
        return createTask(documentId, taskType, STATUS_RUNNING, "Started");
    }

    private RagIndexTask createTask(Long documentId, String taskType, String status, String message) {
        RagIndexTask task = new RagIndexTask();
        task.setDocumentId(documentId);
        task.setTaskType(taskType);
        task.setStatus(status);
        task.setMessage(message);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        ragIndexTaskMapper.insert(task);
        return task;
    }

    private String resolvePipelineStatus(SysDocument document, List<RagDocumentChunk> chunks) {
        String parseStatus = document.getParseStatus();
        if (PARSE_STATUS_PENDING.equalsIgnoreCase(parseStatus)) {
            return "PARSING";
        }
        if (PARSE_STATUS_FAILED.equalsIgnoreCase(parseStatus)) {
            return "FAILED";
        }
        if (chunks.stream().anyMatch(chunk -> STATUS_RUNNING.equalsIgnoreCase(chunk.getIndexStatus()))) {
            return "INDEXING";
        }
        if (chunks.stream().anyMatch(chunk -> STATUS_FAIL.equalsIgnoreCase(chunk.getIndexStatus()))) {
            return "FAILED";
        }
        if (!chunks.isEmpty()) {
            return "INDEXED";
        }
        if (document.getContent() != null && !document.getContent().isBlank()) {
            return "READY_TO_INDEX";
        }
        if (document.getMinioObjectKey() != null && !document.getMinioObjectKey().isBlank()) {
            return "PENDING_PARSE";
        }
        return "EMPTY";
    }

    private String resolvePipelineMessage(SysDocument document, List<RagDocumentChunk> chunks) {
        String status = resolvePipelineStatus(document, chunks);
        return switch (status) {
            case "PARSING" -> "Document text extraction is pending.";
            case "PENDING_PARSE" -> "Original file is stored and waiting for text extraction.";
            case "READY_TO_INDEX" -> "Document text is available but no vector chunks exist for the active profile.";
            case "INDEXING" -> "Vector chunks are being written for the active profile.";
            case "INDEXED" -> "Document is indexed for the active embedding profile.";
            case "FAILED" -> "Parsing or indexing failed. Reprocess the document to retry.";
            default -> "Document has no text or stored source file to index.";
        };
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
                .embeddingProfile(chunk.getEmbeddingProfile())
                .embeddingModel(chunk.getEmbeddingModel())
                .vectorCollection(chunk.getVectorCollection())
                .indexStatus(chunk.getIndexStatus())
                .securityLevel(chunk.getSecurityLevel())
                .deptId(chunk.getDeptId())
                .contentHash(chunk.getContentHash())
                .createTime(chunk.getCreateTime())
                .updateTime(chunk.getUpdateTime())
                .build();
    }
}
