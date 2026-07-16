package com.agent.code.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SQL generation response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeGenerationResponse {

    /** Whether successful */
    private Boolean success;

    /** Generated SQL */
    private String sql;

    /** Original question */
    private String question;

    /** Inference method used: LLM */
    private String inferenceMethod;

    /** Whitelist validation result */
    private Boolean whitelistPassed;

    /** Error message (if any) */
    private String errorMessage;
}
