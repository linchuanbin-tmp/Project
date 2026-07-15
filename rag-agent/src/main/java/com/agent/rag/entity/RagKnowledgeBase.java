package com.agent.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rag_knowledge_base")
public class RagKnowledgeBase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String ownerUsername;

    private Long deptId;

    private String visibility;

    private Integer securityLevel;

    private String status;

    private Integer documentCount;

    private Integer chunkCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
