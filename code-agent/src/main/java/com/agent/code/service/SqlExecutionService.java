package com.agent.code.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SQL 安全执行服务
 * <p>
 * 通过 Spring JdbcTemplate 执行白名单校验通过的 SQL，
 * 自动使用 PreparedStatement 防注入，结果以 List&lt;Map&gt; 返回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlExecutionService {

    private final JdbcTemplate jdbcTemplate;
    private final SqlValidationService validationService;

    /**
     * 执行 SQL 并返回结果
     *
     * @param sql 通过白名单校验的 SQL
     * @return 执行结果（列名 + 数据行）
     */
    public ExecutionResult execute(String sql) {
        // 执行前再次校验（纵深防御）
        SqlValidationService.ValidationResult validation = validationService.validate(sql);
        if (!validation.passed()) {
            return ExecutionResult.fail("白名单校验失败: " + validation.message());
        }

        long startTime = System.currentTimeMillis();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            long elapsed = System.currentTimeMillis() - startTime;

            List<String> columns = rows.isEmpty()
                    ? List.of()
                    : new ArrayList<>(rows.get(0).keySet());

            log.info("✅ SQL 执行成功，返回 {} 行，耗时 {}ms", rows.size(), elapsed);
            return ExecutionResult.success(columns, rows, elapsed, rows.size());

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("❌ SQL 执行失败 ({}ms): {}", elapsed, e.getMessage());
            return ExecutionResult.fail("SQL 执行错误: " + e.getMessage());
        }
    }

    /**
     * 生成 SQL 并执行（一键式接口）
     */
    public FullResult generateAndExecute(String question, CodeGenerationService generationService) {
        // 1. 生成
        var genResponse = generationService.generateSQL(
                new com.agent.code.dto.CodeGenerationRequest(question, "agent_platform"));

        if (!genResponse.getSuccess() || genResponse.getSql() == null) {
            return FullResult.builder()
                    .success(false)
                    .question(question)
                    .errorMessage("SQL 生成失败: " + genResponse.getErrorMessage())
                    .inferenceMethod(genResponse.getInferenceMethod())
                    .build();
        }

        // 2. 执行
        ExecutionResult execResult = execute(genResponse.getSql());

        return FullResult.builder()
                .success(execResult.success)
                .question(question)
                .sql(genResponse.getSql())
                .inferenceMethod(genResponse.getInferenceMethod())
                .whitelistPassed(execResult.success)
                .columns(execResult.columns)
                .rows(execResult.rows)
                .rowCount(execResult.rowCount)
                .elapsedMs(execResult.elapsedMs)
                .errorMessage(execResult.errorMessage)
                .build();
    }

    // ==================== 内部类 ====================

    public record ExecutionResult(
            boolean success,
            List<String> columns,
            List<Map<String, Object>> rows,
            long elapsedMs,
            int rowCount,
            String errorMessage
    ) {
        public static ExecutionResult success(List<String> columns, List<Map<String, Object>> rows,
                                               long elapsedMs, int rowCount) {
            return new ExecutionResult(true, columns, rows, elapsedMs, rowCount, null);
        }

        public static ExecutionResult fail(String errorMessage) {
            return new ExecutionResult(false, List.of(), List.of(), 0, 0, errorMessage);
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FullResult {
        private Boolean success;
        private String question;
        private String sql;
        private String inferenceMethod;
        private Boolean whitelistPassed;
        private List<String> columns;
        private List<Map<String, Object>> rows;
        private int rowCount;
        private long elapsedMs;
        private String errorMessage;
    }
}
