package com.agent.user.dto;

import lombok.Data;

@Data
public class UpdateUserClearanceRequest {
    private Long userId;
    private Integer clearanceLevel;
}
