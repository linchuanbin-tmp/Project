package com.agent.code.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SQL generation request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeGenerationRequest {

    /** User's natural language question */
    @NotBlank(message = "问题不能为空")
    private String question;

    /** Target database (default agent_platform) */
    private String database = "agent_platform";
}
