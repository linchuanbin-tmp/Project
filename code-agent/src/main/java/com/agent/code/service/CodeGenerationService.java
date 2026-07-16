package com.agent.code.service;

import com.agent.code.dto.CodeGenerationRequest;
import com.agent.code.dto.CodeGenerationResponse;

/**
 * Code generation service interface.
 */
public interface CodeGenerationService {

    /**
     * Generate SQL from a natural language question.
     */
    CodeGenerationResponse generateSQL(CodeGenerationRequest request);

    /**
     * Get the name of the inference method being used.
     */
    String getInferenceMethod();
}
