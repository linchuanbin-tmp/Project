package com.agent.code.controller;

import com.agent.code.dto.CodeGenerationRequest;
import com.agent.code.dto.CodeGenerationResponse;
import com.agent.code.dto.MetadataCacheResponse;
import com.agent.code.service.CodeGenerationService;
import com.agent.code.service.MetadataCacheService;
import com.agent.code.service.SqlExecutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Map;

/**
 * Code Agent REST 接口
 * <p>
 * 通过 Gateway 路由 /api/code/** → code-agent:8084
 */
@Slf4j
@RestController
@RequestMapping("/code")
@RequiredArgsConstructor
public class CodeAgentController {

    private final CodeGenerationService codeGenerationService;
    private final MetadataCacheService metadataCacheService;
    private final SqlExecutionService sqlExecutionService;

    /**
     * 自然语言 → SQL 生成 + 执行（一键式）
     * <p>
     * POST /api/code/query
     */
    @PostMapping("/query")
    public ResponseEntity<SqlExecutionService.FullResult> query(
            @Valid @RequestBody CodeGenerationRequest request) {

        log.info("📩 收到一键查询请求: {}", request.getQuestion());
        SqlExecutionService.FullResult result =
                sqlExecutionService.generateAndExecute(request.getQuestion(), codeGenerationService);
        return ResponseEntity.ok(result);
    }

    /**
     * 自然语言 → SQL 生成（仅预览，不校验不执行）
     * <p>
     * POST /api/code/generate
     */
    @PostMapping("/generate")
    public ResponseEntity<CodeGenerationResponse> generateSQL(
            @Valid @RequestBody CodeGenerationRequest request) {

        log.info("📩 收到 SQL 生成请求: {}", request.getQuestion());

        // 生成 SQL，不做白名单校验（仅预览用途）
        CodeGenerationResponse response = codeGenerationService.generateSQL(request);

        return ResponseEntity.ok(response);
    }

    /**
     * 刷新元数据缓存
     * <p>
     * POST /api/code/metadata/refresh
     */
    @PostMapping("/metadata/refresh")
    public ResponseEntity<MetadataCacheResponse> refreshMetadata() {
        var metadata = metadataCacheService.refreshAllMetadata();
        return ResponseEntity.ok(MetadataCacheResponse.builder()
                .tableCount(metadata.size())
                .tableNames(new ArrayList<>(metadata.keySet()))
                .source("MySQL information_schema")
                .timestamp(System.currentTimeMillis())
                .build());
    }

    /**
     * 获取当前缓存的元数据概览
     * <p>
     * GET /api/code/metadata
     */
    @GetMapping("/metadata")
    public ResponseEntity<MetadataCacheResponse> getMetadata() {
        var metadata = metadataCacheService.getAllTableMetadata();
        return ResponseEntity.ok(MetadataCacheResponse.builder()
                .tableCount(metadata.size())
                .tableNames(new ArrayList<>(metadata.keySet()))
                .source(metadata.isEmpty() ? "empty" : "Redis/MySQL")
                .timestamp(System.currentTimeMillis())
                .build());
    }

    /**
     * 执行已校验的 SQL
     * <p>
     * POST /api/code/execute
     */
    @PostMapping("/execute")
    public ResponseEntity<SqlExecutionService.ExecutionResult> executeSQL(
            @RequestBody Map<String, String> body) {
        String sql = body.get("sql");
        if (sql == null || sql.isBlank()) {
            return ResponseEntity.badRequest().body(
                    SqlExecutionService.ExecutionResult.fail("SQL 不能为空"));
        }
        log.info("🔄 执行 SQL: {}", sql);
        return ResponseEntity.ok(sqlExecutionService.execute(sql));
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "code-agent",
                "inferenceMethod", codeGenerationService.getInferenceMethod()
        ));
    }
}
