package com.agent.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendCodeRequest {

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Please enter a valid email address")
    private String email;
}
