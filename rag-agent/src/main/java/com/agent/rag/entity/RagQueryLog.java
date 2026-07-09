package com.agent.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rag_query_log")
public class RagQueryLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    private String question;

    private String answer;

    private String retrievedDocIds;

    private String blockedDocIds;

    private Integer topK;

    private Integer latencyMs;

    private String status;

    private String errorMsg;

    private LocalDateTime createTime;
}
