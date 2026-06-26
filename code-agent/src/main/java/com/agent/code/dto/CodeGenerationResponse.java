package com.agent.code.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SQL 生成响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeGenerationResponse {

    /** 是否成功 */
    private Boolean success;

    /** 生成的 SQL */
    private String sql;

    /** 原始问题 */
    private String question;

    /** 使用的推理方式: LLM */
    private String inferenceMethod;

    /** 白名单校验结果 */
    private Boolean whitelistPassed;

    /** 错误信息（如有） */
    private String errorMessage;
}
