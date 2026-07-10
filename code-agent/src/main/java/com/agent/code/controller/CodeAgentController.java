package com.agent.code.controller;

import com.agent.code.dto.CodeGenerationRequest;
import com.agent.code.dto.CodeGenerationResponse;
import com.agent.code.dto.MetadataCacheResponse;
import com.agent.code.dto.Result;
import com.agent.code.service.CodeGenerationService;
import com.agent.code.service.MetadataCacheService;
import com.agent.code.service.SqlExecutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/code")
@RequiredArgsConstructor
public class CodeAgentController {

    private final CodeGenerationService codeGenerationService;
    private final MetadataCacheService metadataCacheService;
    private final SqlExecutionService sqlExecutionService;

    @PostMapping("/query")
    public Result<SqlExecutionService.FullResult> query(
            @Valid @RequestBody CodeGenerationRequest request) {
        log.info("📩 收到一键查询请求: {}", request.getQuestion());
        SqlExecutionService.FullResult result =
                sqlExecutionService.generateAndExecute(request.getQuestion(), codeGenerationService);
        return Result.success(result);
    }

    @PostMapping("/generate")
    public Result<CodeGenerationResponse> generateSQL(
            @Valid @RequestBody CodeGenerationRequest request) {
        log.info("📩 收到 SQL 生成请求: {}", request.getQuestion());
        CodeGenerationResponse response = codeGenerationService.generateSQL(request);
        return Result.success(response);
    }

    @PostMapping("/metadata/refresh")
    public Result<MetadataCacheResponse> refreshMetadata() {
        var metadata = metadataCacheService.refreshAllMetadata();
        return Result.success(MetadataCacheResponse.builder()
                .tableCount(metadata.size())
                .tableNames(new ArrayList<>(metadata.keySet()))
                .source("MySQL information_schema")
                .timestamp(System.currentTimeMillis())
                .build());
    }

    @GetMapping("/metadata")
    public Result<MetadataCacheResponse> getMetadata() {
        var metadata = metadataCacheService.getAllTableMetadata();
        return Result.success(MetadataCacheResponse.builder()
                .tableCount(metadata.size())
                .tableNames(new ArrayList<>(metadata.keySet()))
                .source(metadata.isEmpty() ? "empty" : "Redis/MySQL")
                .timestamp(System.currentTimeMillis())
                .build());
    }

    @PostMapping("/execute")
    public Result<SqlExecutionService.ExecutionResult> executeSQL(
            @RequestBody Map<String, String> body) {
        String sql = body.get("sql");
        if (sql == null || sql.isBlank()) {
            return Result.error(400, "SQL 不能为空");
        }
        log.info("🔄 执行 SQL: {}", sql);
        return Result.success(sqlExecutionService.execute(sql));
    }

    @GetMapping("/health")
    public Result<Map<String, String>> health() {
        return Result.success(Map.of(
                "status", "UP",
                "service", "code-agent",
                "inferenceMethod", codeGenerationService.getInferenceMethod()
        ));
    }
}
