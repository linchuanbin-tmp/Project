package com.agent.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rag_source_document")
public class RagSourceDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kbId;

    private Long sysDocumentId;

    private String title;

    private String originalFileName;

    private String fileType;

    private String mimeType;

    private Long fileSize;

    private String storageProvider;

    private String storageBucket;

    private String storageObjectKey;

    private String contentHash;

    private String parsedText;

    private String status;

    private String parserStatus;

    private String indexStatus;

    private Integer chunkCount;

    private Integer securityLevel;

    private Long deptId;

    private String uploadedBy;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
