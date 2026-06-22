package com.agent.code.service;

import com.agent.code.dto.CodeGenerationRequest;
import com.agent.code.dto.CodeGenerationResponse;

/**
 * 代码生成服务接口
 */
public interface CodeGenerationService {

    /**
     * 根据自然语言问题生成 SQL
     */
    CodeGenerationResponse generateSQL(CodeGenerationRequest request);

    /**
     * 获取推理方式名称
     */
    String getInferenceMethod();
}
