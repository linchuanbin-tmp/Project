package com.agent.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NotificationActionRequest {

    @NotNull(message = "Notification ID cannot be null")
    private Long notificationId;

    @NotBlank(message = "Action cannot be blank")
    private String action; // APPROVE or DENY

    private String opinion;
}
