package com.agent.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rag_index_task")
public class RagIndexTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private String taskType;

    private String status;

    private String message;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
