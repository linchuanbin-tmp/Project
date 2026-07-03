package com.agent.user.dto;

import lombok.Data;
import java.util.List;

@Data
public class AddMembersRequest {
    private List<Long> userIds;
    private Long deptId;
}
