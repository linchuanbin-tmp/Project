package com.agent.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    private String password;

    private String code;  // Verification code (optional; if present, code-based login is used)
}