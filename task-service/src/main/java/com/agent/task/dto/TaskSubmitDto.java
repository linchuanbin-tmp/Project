package com.agent.task.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TaskSubmitDto {

    @NotBlank(message = "Task type cannot be blank")
    private String taskType;

    @NotBlank(message = "Input prompt cannot be blank")
    private String input;
}
