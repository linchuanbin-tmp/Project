package com.agent.user.dto;

import lombok.Data;

@Data
public class RemoveMemberRequest {
    private Long userId;
    private Long deptId;
}
