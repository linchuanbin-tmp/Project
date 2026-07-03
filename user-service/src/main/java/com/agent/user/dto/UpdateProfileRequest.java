package com.agent.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @NotBlank(message = "Real name cannot be blank")
    @Size(max = 50, message = "Real name must not exceed 50 characters")
    private String realName;
}
