package com.agent.rag.service.impl;

import com.agent.rag.dto.DocumentUploadResponse;
import com.agent.rag.dto.KnowledgeBaseRequest;
import com.agent.rag.dto.KnowledgeBaseResponse;
import com.agent.rag.dto.SourceDocumentResponse;
import com.agent.rag.dto.StoredDocument;
import com.agent.rag.entity.RagKnowledgeBase;
import com.agent.rag.entity.RagSourceDocument;
import com.agent.rag.entity.SysDocument;
import com.agent.rag.mapper.RagKnowledgeBaseMapper;
import com.agent.rag.mapper.RagSourceDocumentMapper;
import com.agent.rag.mapper.SysDocumentMapper;
import com.agent.rag.service.DocumentStorageService;
import com.agent.rag.service.RagKnowledgeBaseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RagKnowledgeBaseServiceImpl implements RagKnowledgeBaseService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DELETED = "DELETED";
    private static final String DEFAULT_VISIBILITY = "DEPARTMENT";
    private static final String PARSER_STATUS_PARSED = "PARSED";
    private static final String PARSER_STATUS_PENDING = "PARSE_PENDING";
    private static final String INDEX_STATUS_PENDING = "PENDING";
    private static final String INDEX_STATUS_PARSE_PENDING = "PARSE_PENDING";

    private final RagKnowledgeBaseMapper knowledgeBaseMapper;
    private final RagSourceDocumentMapper sourceDocumentMapper;
    private final SysDocumentMapper sysDocumentMapper;
    private final DocumentStorageService documentStorageService;

    @Override
    public List<KnowledgeBaseResponse> listKnowledgeBases() {
        return knowledgeBaseMapper.selectList(
                        new LambdaQueryWrapper<RagKnowledgeBase>()
                                .ne(RagKnowledgeBase::getStatus, STATUS_DELETED)
                                .orderByDesc(RagKnowledgeBase::getUpdateTime)
                )
                .stream()
                .map(this::toKnowledgeBaseResponse)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseResponse createKnowledgeBase(KnowledgeBaseRequest request, String username) {
        LocalDateTime now = LocalDateTime.now();
        RagKnowledgeBase entity = new RagKnowledgeBase();
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setOwnerUsername(username);
        entity.setDeptId(request.getDeptId());
        entity.setVisibility(normalizeVisibility(request.getVisibility()));
        entity.setSecurityLevel(defaultSecurityLevel(request.getSecurityLevel()));
        entity.setStatus(STATUS_ACTIVE);
        entity.setDocumentCount(0);
        entity.setChunkCount(0);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        knowledgeBaseMapper.insert(entity);
        return toKnowledgeBaseResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseResponse updateKnowledgeBase(Long kbId, KnowledgeBaseRequest request) {
        RagKnowledgeBase entity = requireKnowledgeBase(kbId);
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setDeptId(request.getDeptId());
        entity.setVisibility(normalizeVisibility(request.getVisibility()));
        entity.setSecurityLevel(defaultSecurityLevel(request.getSecurityLevel()));
        entity.setUpdateTime(LocalDateTime.now());
        knowledgeBaseMapper.updateById(entity);
        return toKnowledgeBaseResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeBase(Long kbId) {
        RagKnowledgeBase entity = requireKnowledgeBase(kbId);
        entity.setStatus(STATUS_DELETED);
        entity.setUpdateTime(LocalDateTime.now());
        knowledgeBaseMapper.updateById(entity);
    }

    @Override
    public List<SourceDocumentResponse> listDocuments(Long kbId) {
        requireKnowledgeBase(kbId);
        return sourceDocumentMapper.selectList(
                        new LambdaQueryWrapper<RagSourceDocument>()
                                .eq(RagSourceDocument::getKbId, kbId)
                                .eq(RagSourceDocument::getStatus, STATUS_ACTIVE)
                                .orderByDesc(RagSourceDocument::getCreateTime)
                )
                .stream()
                .map(this::toSourceDocumentResponse)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentUploadResponse uploadDocuments(
            Long kbId,
            MultipartFile[] files,
            Long deptId,
            Integer securityLevel,
            String username
    ) {
        RagKnowledgeBase knowledgeBase = requireKnowledgeBase(kbId);
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("At least one file is required.");
        }

        List<SourceDocumentResponse> responses = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            responses.add(uploadOne(knowledgeBase, file, deptId, securityLevel, username));
        }

        if (!responses.isEmpty()) {
            knowledgeBase.setDocumentCount((knowledgeBase.getDocumentCount() == null ? 0 : knowledgeBase.getDocumentCount())
                    + responses.size());
            knowledgeBase.setUpdateTime(LocalDateTime.now());
            knowledgeBaseMapper.updateById(knowledgeBase);
        }

        return DocumentUploadResponse.builder()
                .kbId(kbId)
                .uploadedCount(responses.size())
                .documents(responses)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long kbId, Long documentId) {
        requireKnowledgeBase(kbId);
        RagSourceDocument document = sourceDocumentMapper.selectOne(
                new LambdaQueryWrapper<RagSourceDocument>()
                        .eq(RagSourceDocument::getKbId, kbId)
                        .eq(RagSourceDocument::getId, documentId)
        );
        if (document == null || STATUS_DELETED.equals(document.getStatus())) {
            throw new IllegalArgumentException("Source document not found: " + documentId);
        }
        document.setStatus(STATUS_DELETED);
        document.setUpdateTime(LocalDateTime.now());
        sourceDocumentMapper.updateById(document);
    }

    private SourceDocumentResponse uploadOne(
            RagKnowledgeBase knowledgeBase,
            MultipartFile file,
            Long deptId,
            Integer securityLevel,
            String username
    ) {
        try {
            byte[] content = file.getBytes();
            String originalFileName = normalizeFileName(file.getOriginalFilename());
            String fileType = detectFileType(originalFileName);
            String parsedText = parsePlainTextIfSupported(fileType, file.getContentType(), content);
            boolean parsed = parsedText != null && !parsedText.isBlank();
            LocalDateTime now = LocalDateTime.now();

            RagSourceDocument source = new RagSourceDocument();
            source.setKbId(knowledgeBase.getId());
            source.setTitle(buildTitle(originalFileName));
            source.setOriginalFileName(originalFileName);
            source.setFileType(fileType);
            source.setMimeType(file.getContentType());
            source.setFileSize(file.getSize());
            source.setStorageProvider("minio");
            source.setContentHash(sha256(content));
            source.setParsedText(parsed ? parsedText : null);
            source.setStatus(STATUS_ACTIVE);
            source.setParserStatus(parsed ? PARSER_STATUS_PARSED : PARSER_STATUS_PENDING);
            source.setIndexStatus(parsed ? INDEX_STATUS_PENDING : INDEX_STATUS_PARSE_PENDING);
            source.setChunkCount(0);
            source.setSecurityLevel(defaultSecurityLevel(securityLevel != null
                    ? securityLevel
                    : knowledgeBase.getSecurityLevel()));
            source.setDeptId(deptId != null ? deptId : knowledgeBase.getDeptId());
            source.setUploadedBy(username);
            source.setCreateTime(now);
            source.setUpdateTime(now);
            sourceDocumentMapper.insert(source);

            StoredDocument storedDocument = documentStorageService.storeOriginal(
                    knowledgeBase.getId(),
                    source.getId(),
                    originalFileName,
                    file.getContentType(),
                    content
            );
            source.setStorageProvider(storedDocument.getProvider());
            source.setStorageBucket(storedDocument.getBucket());
            source.setStorageObjectKey(storedDocument.getObjectKey());

            if (parsed) {
                SysDocument sysDocument = new SysDocument();
                sysDocument.setTitle(source.getTitle());
                sysDocument.setContent(parsedText);
                sysDocument.setDeptId(source.getDeptId());
                sysDocument.setSecurityLevel(source.getSecurityLevel());
                sysDocument.setCreateTime(now);
                sysDocumentMapper.insert(sysDocument);
                source.setSysDocumentId(sysDocument.getId());
            }

            source.setUpdateTime(LocalDateTime.now());
            sourceDocumentMapper.updateById(source);
            return toSourceDocumentResponse(source);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to upload document " + file.getOriginalFilename()
                    + ": " + e.getMessage(), e);
        }
    }

    private RagKnowledgeBase requireKnowledgeBase(Long kbId) {
        RagKnowledgeBase entity = knowledgeBaseMapper.selectById(kbId);
        if (entity == null || STATUS_DELETED.equals(entity.getStatus())) {
            throw new IllegalArgumentException("Knowledge base not found: " + kbId);
        }
        return entity;
    }

    private String normalizeVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return DEFAULT_VISIBILITY;
        }
        String normalized = visibility.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PRIVATE", "DEPARTMENT", "GLOBAL" -> normalized;
            default -> DEFAULT_VISIBILITY;
        };
    }

    private Integer defaultSecurityLevel(Integer securityLevel) {
        if (securityLevel == null || securityLevel < 1 || securityLevel > 3) {
            return 1;
        }
        return securityLevel;
    }

    private String detectFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeFileName(String fileName) {
        return fileName == null || fileName.isBlank() ? "document" : fileName;
    }

    private String buildTitle(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "Untitled document";
        }
        int dotIndex = originalFileName.lastIndexOf('.');
        return dotIndex > 0 ? originalFileName.substring(0, dotIndex) : originalFileName;
    }

    private String parsePlainTextIfSupported(String fileType, String contentType, byte[] content) {
        boolean plainText = contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("text/");
        boolean supportedExtension = List.of("txt", "md", "markdown", "csv", "json", "yaml", "yml", "sql", "log")
                .contains(fileType);
        if (!plainText && !supportedExtension) {
            return null;
        }
        return new String(content, StandardCharsets.UTF_8);
    }

    private String sha256(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(content));
    }

    private KnowledgeBaseResponse toKnowledgeBaseResponse(RagKnowledgeBase entity) {
        return KnowledgeBaseResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .ownerUsername(entity.getOwnerUsername())
                .deptId(entity.getDeptId())
                .visibility(entity.getVisibility())
                .securityLevel(entity.getSecurityLevel())
                .status(entity.getStatus())
                .documentCount(entity.getDocumentCount())
                .chunkCount(entity.getChunkCount())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    private SourceDocumentResponse toSourceDocumentResponse(RagSourceDocument entity) {
        return SourceDocumentResponse.builder()
                .id(entity.getId())
                .kbId(entity.getKbId())
                .sysDocumentId(entity.getSysDocumentId())
                .title(entity.getTitle())
                .originalFileName(entity.getOriginalFileName())
                .fileType(entity.getFileType())
                .mimeType(entity.getMimeType())
                .fileSize(entity.getFileSize())
                .storageProvider(entity.getStorageProvider())
                .storageBucket(entity.getStorageBucket())
                .storageObjectKey(entity.getStorageObjectKey())
                .contentHash(entity.getContentHash())
                .status(entity.getStatus())
                .parserStatus(entity.getParserStatus())
                .indexStatus(entity.getIndexStatus())
                .chunkCount(entity.getChunkCount())
                .securityLevel(entity.getSecurityLevel())
                .deptId(entity.getDeptId())
                .uploadedBy(entity.getUploadedBy())
                .errorMessage(entity.getErrorMessage())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
