package com.agent.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Please enter a valid email address")
    private String username;   // email is used as the username

    @NotBlank(message = "Password cannot be blank")
    private String password;

    private String realName;
}
