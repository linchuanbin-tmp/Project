package com.agent.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_document")
public class SysDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private Long deptId;

    private Integer securityLevel;

    private LocalDateTime createTime;

    private String fileType;

    private Long fileSize;

    private String minioObjectKey;

    private String parseStatus;
}
