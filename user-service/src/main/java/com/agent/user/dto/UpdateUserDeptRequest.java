package com.agent.user.dto;

import lombok.Data;

@Data
public class UpdateUserDeptRequest {
    private Long userId;
    private Long deptId;
}
