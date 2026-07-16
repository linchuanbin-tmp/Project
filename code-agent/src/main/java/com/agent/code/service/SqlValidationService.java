package com.agent.code.service;

import com.agent.code.config.CodeAgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL whitelist validation service.
 * <p>
 * Multi-layer defense strategy:
 * 1. Operation type validation — only SELECT is allowed
 * 2. Keyword blacklist — block dangerous keywords
 * 3. Table name whitelist — only allow queries on tables present in information_schema
 * 4. Column name whitelist — only allow queries on columns that actually exist in the tables
 * 5. Complexity limits — restrict JOIN table count and condition count
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlValidationService {

    private final CodeAgentProperties properties;
    private final MetadataCacheService metadataCacheService;

    /** Pattern to match table names: FROM table_name, JOIN table_name */
    private static final Pattern TABLE_PATTERN =
            Pattern.compile("\\b(?:FROM|JOIN)\\s+`?(\\w+)`?", Pattern.CASE_INSENSITIVE);

    /** Pattern to match each column in SELECT */
    private static final Pattern SELECT_COLUMNS_PATTERN =
            Pattern.compile("SELECT\\s+(.+?)\\s+FROM", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Validate SQL against the whitelist.
     *
     * @param sql       the SQL to validate
     * @param userRoles the current user's role list (used for sensitive column checks;
     *                  non-admin roles will be denied access to sensitive columns)
     * @return validation result
     */
    public ValidationResult validate(String sql, List<String> userRoles) {
        if (sql == null || sql.isBlank()) {
            return ValidationResult.fail("SQL 不能为空");
        }

        String normalizedSql = normalize(sql);

        // Layer 1: check operation type
        ValidationResult opResult = validateOperation(normalizedSql);
        if (!opResult.passed) return opResult;

        // Layer 2: check forbidden keywords
        ValidationResult kwResult = validateForbiddenKeywords(normalizedSql);
        if (!kwResult.passed) return kwResult;

        // Layer 3: check table name whitelist
        ValidationResult tableResult = validateTableNames(normalizedSql);
        if (!tableResult.passed) return tableResult;

        // Layer 4: check column name whitelist
        ValidationResult colResult = validateColumnNames(normalizedSql);
        if (!colResult.passed) return colResult;

        // Layer 5: check query complexity
        ValidationResult complexityResult = validateComplexity(normalizedSql);
        if (!complexityResult.passed) return complexityResult;

        // Layer 6: check sensitive columns (only for non-admin users)
        ValidationResult sensitiveResult = validateSensitiveColumns(normalizedSql, userRoles);
        if (!sensitiveResult.passed) return sensitiveResult;

        return ValidationResult.pass();
    }

    // ==================== Validation Layers ====================

    /**
     * Layer 1: Operation type validation.
     */
    private ValidationResult validateOperation(String sql) {
        String upperSql = sql.toUpperCase().trim();
        List<String> allowedOps = properties.getWhitelist().getAllowedOperations();

        for (String op : allowedOps) {
            if (upperSql.startsWith(op)) {
                return ValidationResult.pass();
            }
        }

        return ValidationResult.fail(
                String.format("仅允许 %s 操作，当前 SQL: %s", allowedOps, sql.substring(0, Math.min(50, sql.length())))
        );
    }

    /**
     * Layer 2: Forbidden keywords blacklist.
     */
    private ValidationResult validateForbiddenKeywords(String sql) {
        String upperSql = sql.toUpperCase();
        List<String> forbidden = properties.getWhitelist().getForbiddenKeywords();

        for (String keyword : forbidden) {
            // Use regex word-boundary matching to avoid false positives (e.g. IN inside INT)
            Pattern p = Pattern.compile("\\b" + keyword + "\\b", Pattern.CASE_INSENSITIVE);
            if (p.matcher(upperSql).find()) {
                return ValidationResult.fail("SQL 包含禁用关键字: " + keyword);
            }
        }
        return ValidationResult.pass();
    }

    /**
     * Layer 3: Table name whitelist validation.
     */
    private ValidationResult validateTableNames(String sql) {
        Set<String> allowedTables = metadataCacheService.getAllowedTableNames();

        if (allowedTables.isEmpty()) {
            log.warn("⚠️ 元数据缓存为空，跳过表名校验。请先刷新元数据缓存。");
            return ValidationResult.pass();
        }

        Matcher matcher = TABLE_PATTERN.matcher(sql);
        Set<String> referencedTables = new HashSet<>();
        while (matcher.find()) {
            referencedTables.add(matcher.group(1).toLowerCase());
        }

        for (String table : referencedTables) {
            boolean allowed = allowedTables.stream()
                    .anyMatch(t -> t.equalsIgnoreCase(table));
            if (!allowed) {
                return ValidationResult.fail(
                        String.format("表 '%s' 不在白名单中。允许的表: %s", table, allowedTables)
                );
            }
        }

        log.debug("✅ 表名校验通过: {}", referencedTables);
        return ValidationResult.pass();
    }

    /**
     * Layer 4: Column name whitelist validation.
     * <p>
     * For JOIN queries, all columns of referenced tables are allowed.
     * For aliases (e.g. c.name), validation is performed against all referenced table columns.
     */
    private ValidationResult validateColumnNames(String sql) {
        // If SELECT *, allow (simplified handling)
        String upperSql = sql.toUpperCase();
        if (upperSql.contains("SELECT *") || upperSql.contains("SELECT COUNT(*)")) {
            return ValidationResult.pass();
        }

        // Extract SELECT columns before FROM
        Matcher selectMatcher = SELECT_COLUMNS_PATTERN.matcher(sql);
        if (!selectMatcher.find()) {
            return ValidationResult.pass(); // unable to parse columns, treat leniently
        }

        String columnsPart = selectMatcher.group(1);

        // Get all referenced tables (including JOINed tables)
        Matcher tableMatcher = TABLE_PATTERN.matcher(sql);
        Set<String> allTables = new HashSet<>();
        while (tableMatcher.find()) {
            allTables.add(tableMatcher.group(1));
        }
        if (allTables.isEmpty()) {
            return ValidationResult.pass();
        }

        // Merge allowed columns from all referenced tables
        Set<String> allAllowedColumns = new HashSet<>();
        for (String table : allTables) {
            allAllowedColumns.addAll(metadataCacheService.getAllowedColumnNames(table));
        }
        if (allAllowedColumns.isEmpty()) {
            return ValidationResult.pass(); // skip if no metadata
        }

        // Extract each column name
        Set<String> usedColumns = extractColumnNames(columnsPart);

        for (String col : usedColumns) {
            // Skip aggregate functions and expressions
            if (isAggregateOrExpression(col)) continue;

            boolean allowed = allAllowedColumns.stream()
                    .anyMatch(c -> c.equalsIgnoreCase(col));
            if (!allowed) {
                return ValidationResult.fail(
                        String.format("列 '%s' 不在任何引用表的白名单中", col)
                );
            }
        }

        return ValidationResult.pass();
    }

    /**
     * Layer 5: Complexity limits
     */
    private ValidationResult validateComplexity(String sql) {
        String upperSql = sql.toUpperCase();
        int maxTables = properties.getWhitelist().getMaxTablesPerQuery();

        // Count JOINed tables
        Matcher tableMatcher = TABLE_PATTERN.matcher(sql);
        int tableCount = 0;
        while (tableMatcher.find()) tableCount++;

        if (tableCount > maxTables) {
            return ValidationResult.fail(
                    String.format("查询涉及 %d 张表，超过限制 %d", tableCount, maxTables)
            );
        }

        // Count WHERE conditions (split by AND/OR)
        int maxConditions = properties.getWhitelist().getMaxConditions();
        int andCount = countOccurrences(upperSql, "\\bAND\\b");
        int orCount = countOccurrences(upperSql, "\\bOR\\b");
        int conditionCount = andCount + orCount + 1; // +1 for the base condition

        if (conditionCount > maxConditions) {
            return ValidationResult.fail(
                    String.format("查询有 %d 个条件，超过限制 %d", conditionCount, maxConditions)
            );
        }

        return ValidationResult.pass();
    }

    // ==================== Helper Methods ====================

    /**
     * Normalize SQL (remove excess whitespace)
     */
    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    /**
     * Extract column names from the SELECT column list
     */
    private Set<String> extractColumnNames(String columnsPart) {
        Set<String> columns = new HashSet<>();
        // Split by comma
        String[] parts = columnsPart.split(",");
        for (String part : parts) {
            part = part.trim();
            // Extract the last word as column name (handle "t.col" -> "col", "col AS alias" -> "col")
            // Handle table.column format first
            if (part.contains(".")) {
                part = part.substring(part.lastIndexOf('.') + 1);
            }
            // Extract the first word (skip aggregate functions)
            String[] words = part.split("\\s+");
            if (words.length > 0) {
                String col = words[0].replaceAll("[^a-zA-Z0-9_]", "");
                if (!col.isEmpty()) {
                    columns.add(col);
                }
            }
        }
        return columns;
    }

    /**
     * Check if the column is an aggregate function or expression
     */
    private boolean isAggregateOrExpression(String col) {
        String upper = col.toUpperCase();
        return upper.matches("COUNT|SUM|AVG|MAX|MIN|DISTINCT|CASE|WHEN|THEN|ELSE|END|CAST|COALESCE")
                || col.contains("(");
    }

    /**
     * Count regex matches
     */
    private int countOccurrences(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    /**
     * Layer 6: Sensitive column validation.
     * <p>
     * Only enforced for non-admin users. If the query references sensitive columns
     * and the user is not ROLE_ADMIN, validation fails — the caller should route
     * it to the HITL approval flow.
     */
    private ValidationResult validateSensitiveColumns(String sql, List<String> userRoles) {
        // Admins are exempt from sensitive column restrictions
        if (userRoles != null && userRoles.stream().anyMatch(r -> "ROLE_ADMIN".equalsIgnoreCase(r))) {
            return ValidationResult.pass();
        }
        // Fallback when no role info is provided; do not block (avoid breaking anonymous/unauthenticated scenarios, let upstream decide)
        if (userRoles == null || userRoles.isEmpty()) {
            return ValidationResult.pass();
        }

        List<String> sensitiveCols = properties.getWhitelist().getSensitiveColumns();
        if (sensitiveCols == null || sensitiveCols.isEmpty()) {
            return ValidationResult.pass();
        }

        // For SELECT *, check the full table — lenient handling, only log a warning
        String upperSql = sql.toUpperCase();
        if (upperSql.contains("SELECT *") || upperSql.contains("SELECT COUNT(*)")) {
            log.info("⚠️ SELECT * query by non-admin may include sensitive columns; consider HITL review");
            return ValidationResult.pass(); // Do not block, let the HITL mechanism handle it
        }

        // Extract SELECT columns and check against sensitive columns
        Matcher selectMatcher = SELECT_COLUMNS_PATTERN.matcher(sql);
        if (selectMatcher.find()) {
            String columnsPart = selectMatcher.group(1);
            Set<String> usedColumns = extractColumnNames(columnsPart);
            for (String col : usedColumns) {
                for (String sensitive : sensitiveCols) {
                    if (sensitive.equalsIgnoreCase(col)) {
                        return ValidationResult.fail(
                                "查询包含敏感列 '" + col + "'，非管理员用户需通过 HITL 审批");
                    }
                }
            }
        }

        return ValidationResult.pass();
    }

    // ==================== Validation Result Class ====================

    public record ValidationResult(boolean passed, String message) {
        public static ValidationResult pass() {
            return new ValidationResult(true, "✅ 白名单校验通过");
        }

        public static ValidationResult fail(String message) {
            log.warn("❌ SQL 白名单校验失败: {}", message);
            return new ValidationResult(false, message);
        }
    }

    /**
     * Backward compatible: validation method without role parameter.
     * Does not perform sensitive column checks (equivalent to admin privileges).
     */
    public ValidationResult validate(String sql) {
        return validate(sql, null);
    }
}
