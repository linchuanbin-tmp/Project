package com.agent.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NotificationSendRequest {

    @NotNull(message = "Receiver ID cannot be null")
    private Long receiverId;

    @NotBlank(message = "Title cannot be blank")
    private String title;

    @NotBlank(message = "Content cannot be blank")
    private String content;

    private String notifyType = "CHAT";

    private String payload;
}
