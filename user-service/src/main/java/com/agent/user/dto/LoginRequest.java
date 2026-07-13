package com.agent.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    private String password;

    private String code;  // 验证码登录（可选，有值则走验证码登录）
}