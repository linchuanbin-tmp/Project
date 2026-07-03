package com.agent.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignRoleRequest {
    @NotNull(message = "User ID cannot be null")
    private Long userId;

    @NotNull(message = "Role ID cannot be null")
    private Long roleId;
}
