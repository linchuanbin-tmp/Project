package com.agent.code.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SQL secure execution service
 * <p>
 * Execute whitelisted SQL via Spring JdbcTemplate,
 * automatically use PreparedStatement to prevent injection, results returned as List&lt;Map&gt;.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlExecutionService {

    private final JdbcTemplate jdbcTemplate;
    private final SqlValidationService validationService;

    /**
     * Execute SQL and return results
     *
     * @param sql SQL that passed whitelist validation
     * @return execution result (column names + data rows)
     */
    public ExecutionResult execute(String sql) {
        // Re-validate before execution (defense in depth)
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
     * Generate SQL and execute (one-click interface)
     */
    public FullResult generateAndExecute(String question, CodeGenerationService generationService) {
        // Step 1. Generate
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

        // Step 2. Execute
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

    // ==================== Inner Classes ====================

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
