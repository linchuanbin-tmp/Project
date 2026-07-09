package com.agent.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task_record")
public class TaskRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskType;

    private String status;

    private Long userId;

    private String input;

    private String output;

    private String errorMsg;

    private Integer attemptCount;

    private Integer elapsedTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
