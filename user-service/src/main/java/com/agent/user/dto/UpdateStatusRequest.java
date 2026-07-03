package com.agent.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStatusRequest {
    @NotNull(message = "User ID cannot be null")
    private Long userId;

    @NotNull(message = "Status cannot be null")
    private Integer status;
}
