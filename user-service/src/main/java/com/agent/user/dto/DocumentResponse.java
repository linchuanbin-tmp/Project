package com.agent.user.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DocumentResponse {
    private Long id;
    private String title;
    private String content;
    private Long deptId;
    private Integer securityLevel;
    private Boolean accessible;
    private LocalDateTime createTime;
}
