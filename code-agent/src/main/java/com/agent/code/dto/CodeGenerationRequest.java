package com.agent.code.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SQL 生成请求 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeGenerationRequest {

    /** 用户的自然语言问题 */
    @NotBlank(message = "问题不能为空")
    private String question;

    /** 目标数据库（默认 agent_platform） */
    private String database = "agent_platform";
}
