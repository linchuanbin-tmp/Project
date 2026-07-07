package com.agent.user.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NotificationResponse {
    private Long id;
    private Long senderId;
    private String senderName;
    private String senderRealName;
    private Long receiverId;
    private String receiverName;
    private String receiverRealName;
    private String title;
    private String content;
    private String notifyType;
    private Integer status;
    private String payload;
    private Long parentId;
    private Long threadId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
