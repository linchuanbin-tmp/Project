package com.agent.user.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class UserInfoResponse {
    private String username;
    private String realName;
    private List<String> roles;
    private List<String> permissions;
    private Long deptId;
    private String deptName;
    private Integer clearanceLevel;

    public UserInfoResponse(String username, String realName, List<String> roles, List<String> permissions, Long deptId, String deptName, Integer clearanceLevel) {
        this.username = username;
        this.realName = realName;
        this.roles = roles;
        this.permissions = permissions;
        this.deptId = deptId;
        this.deptName = deptName;
        this.clearanceLevel = clearanceLevel;
    }
}
