package com.agent.user.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserResponse {
    private Long id;
    private String username;
    private String realName;
    private Integer status;
    private LocalDateTime createTime;
    private List<String> roles;
}
