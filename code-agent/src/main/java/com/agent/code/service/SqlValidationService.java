package com.agent.code.service;

import com.agent.code.config.CodeAgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 白名单校验服务
 * <p>
 * 多层防护策略：
 * 1. 操作类型校验 —— 仅允许 SELECT
 * 2. 关键字黑名单 —— 拦截危险关键字
 * 3. 表名白名单 —— 只允许查询 information_schema 中存在的表
 * 4. 列名白名单 —— 只允许查询表中实际存在的列
 * 5. 复杂度限制 —— 限制 JOIN 表数、条件数
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlValidationService {

    private final CodeAgentProperties properties;
    private final MetadataCacheService metadataCacheService;

    /** 匹配表名的模式: FROM table_name, JOIN table_name */
    private static final Pattern TABLE_PATTERN =
            Pattern.compile("\\b(?:FROM|JOIN)\\s+`?(\\w+)`?", Pattern.CASE_INSENSITIVE);

    /** 匹配 SELECT 中的每个列 */
    private static final Pattern SELECT_COLUMNS_PATTERN =
            Pattern.compile("SELECT\\s+(.+?)\\s+FROM", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * 校验 SQL 是否通过白名单
     *
     * @param sql       待校验的 SQL
     * @param userRoles 当前用户的角色列表（用于敏感列判断，非管理员角色时敏感列将被拒绝）
     * @return 校验结果
     */
    public ValidationResult validate(String sql, List<String> userRoles) {
        if (sql == null || sql.isBlank()) {
            return ValidationResult.fail("SQL 不能为空");
        }

        String normalizedSql = normalize(sql);

        // 第一层：检查操作类型
        ValidationResult opResult = validateOperation(normalizedSql);
        if (!opResult.passed) return opResult;

        // 第二层：检查禁用关键字
        ValidationResult kwResult = validateForbiddenKeywords(normalizedSql);
        if (!kwResult.passed) return kwResult;

        // 第三层：检查表名白名单
        ValidationResult tableResult = validateTableNames(normalizedSql);
        if (!tableResult.passed) return tableResult;

        // 第四层：检查列名白名单
        ValidationResult colResult = validateColumnNames(normalizedSql);
        if (!colResult.passed) return colResult;

        // 第五层：检查查询复杂度
        ValidationResult complexityResult = validateComplexity(normalizedSql);
        if (!complexityResult.passed) return complexityResult;

        // 第六层：检查敏感列（仅对非管理员生效）
        ValidationResult sensitiveResult = validateSensitiveColumns(normalizedSql, userRoles);
        if (!sensitiveResult.passed) return sensitiveResult;

        return ValidationResult.pass();
    }

    // ==================== 各层校验 ====================

    /**
     * 第一层：操作类型校验
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
     * 第二层：禁用关键字黑名单
     */
    private ValidationResult validateForbiddenKeywords(String sql) {
        String upperSql = sql.toUpperCase();
        List<String> forbidden = properties.getWhitelist().getForbiddenKeywords();

        for (String keyword : forbidden) {
            // 用正则单词边界匹配，避免误判（如 INT 中的 IN）
            Pattern p = Pattern.compile("\\b" + keyword + "\\b", Pattern.CASE_INSENSITIVE);
            if (p.matcher(upperSql).find()) {
                return ValidationResult.fail("SQL 包含禁用关键字: " + keyword);
            }
        }
        return ValidationResult.pass();
    }

    /**
     * 第三层：表名白名单校验
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
     * 第四层：列名白名单校验
     * <p>
     * 对于 JOIN 查询，允许任意引用表的所有列。
     * 对于别名（如 c.name），按所有引用表的列来校验。
     */
    private ValidationResult validateColumnNames(String sql) {
        // 如果是 SELECT *，允许（简化处理）
        String upperSql = sql.toUpperCase();
        if (upperSql.contains("SELECT *") || upperSql.contains("SELECT COUNT(*)")) {
            return ValidationResult.pass();
        }

        // 提取 FROM 之前的 SELECT 列
        Matcher selectMatcher = SELECT_COLUMNS_PATTERN.matcher(sql);
        if (!selectMatcher.find()) {
            return ValidationResult.pass(); // 无法解析列，宽松处理
        }

        String columnsPart = selectMatcher.group(1);

        // 获取所有被引用的表（包括 JOIN 的表）
        Matcher tableMatcher = TABLE_PATTERN.matcher(sql);
        Set<String> allTables = new HashSet<>();
        while (tableMatcher.find()) {
            allTables.add(tableMatcher.group(1));
        }
        if (allTables.isEmpty()) {
            return ValidationResult.pass();
        }

        // 合并所有引用表允许的列
        Set<String> allAllowedColumns = new HashSet<>();
        for (String table : allTables) {
            allAllowedColumns.addAll(metadataCacheService.getAllowedColumnNames(table));
        }
        if (allAllowedColumns.isEmpty()) {
            return ValidationResult.pass(); // 无元数据则跳过
        }

        // 提取每个列名
        Set<String> usedColumns = extractColumnNames(columnsPart);

        for (String col : usedColumns) {
            // 跳过聚合函数和表达式
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
     * 第五层：复杂度限制
     */
    private ValidationResult validateComplexity(String sql) {
        String upperSql = sql.toUpperCase();
        int maxTables = properties.getWhitelist().getMaxTablesPerQuery();

        // 统计 JOIN 表数
        Matcher tableMatcher = TABLE_PATTERN.matcher(sql);
        int tableCount = 0;
        while (tableMatcher.find()) tableCount++;

        if (tableCount > maxTables) {
            return ValidationResult.fail(
                    String.format("查询涉及 %d 张表，超过限制 %d", tableCount, maxTables)
            );
        }

        // 统计 WHERE 条件数（按 AND/OR 分割）
        int maxConditions = properties.getWhitelist().getMaxConditions();
        int andCount = countOccurrences(upperSql, "\\bAND\\b");
        int orCount = countOccurrences(upperSql, "\\bOR\\b");
        int conditionCount = andCount + orCount + 1; // +1 为基础条件

        if (conditionCount > maxConditions) {
            return ValidationResult.fail(
                    String.format("查询有 %d 个条件，超过限制 %d", conditionCount, maxConditions)
            );
        }

        return ValidationResult.pass();
    }

    // ==================== 辅助方法 ====================

    /**
     * 标准化 SQL（去除多余空白）
     */
    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    /**
     * 从 SELECT 列部分提取列名
     */
    private Set<String> extractColumnNames(String columnsPart) {
        Set<String> columns = new HashSet<>();
        // 按逗号分割
        String[] parts = columnsPart.split(",");
        for (String part : parts) {
            part = part.trim();
            // 提取最后一个词作为列名（处理 "t.col" → "col", "col AS alias" → "col"）
            // 先处理 table.column 形式
            if (part.contains(".")) {
                part = part.substring(part.lastIndexOf('.') + 1);
            }
            // 提取第一个词（跳过聚合函数）
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
     * 判断是否是聚合函数或表达式
     */
    private boolean isAggregateOrExpression(String col) {
        String upper = col.toUpperCase();
        return upper.matches("COUNT|SUM|AVG|MAX|MIN|DISTINCT|CASE|WHEN|THEN|ELSE|END|CAST|COALESCE")
                || col.contains("(");
    }

    /**
     * 统计正则匹配次数
     */
    private int countOccurrences(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    /**
     * 第六层：敏感列校验。
     * <p>
     * 仅对非管理员用户生效。如果查询引用了敏感列且用户不是 ROLE_ADMIN，
     * 校验失败 —— 调用方应将其路由到 HITL 审批流程。
     */
    private ValidationResult validateSensitiveColumns(String sql, List<String> userRoles) {
        // 管理员不受敏感列限制
        if (userRoles != null && userRoles.stream().anyMatch(r -> "ROLE_ADMIN".equalsIgnoreCase(r))) {
            return ValidationResult.pass();
        }
        // 未传入角色信息时回退，不阻止（避免破坏匿名/未认证场景，由上游决定）
        if (userRoles == null || userRoles.isEmpty()) {
            return ValidationResult.pass();
        }

        List<String> sensitiveCols = properties.getWhitelist().getSensitiveColumns();
        if (sensitiveCols == null || sensitiveCols.isEmpty()) {
            return ValidationResult.pass();
        }

        // 如果是 SELECT *，检查全表 —— 宽松处理，仅标记警告
        String upperSql = sql.toUpperCase();
        if (upperSql.contains("SELECT *") || upperSql.contains("SELECT COUNT(*)")) {
            log.info("⚠️ SELECT * query by non-admin may include sensitive columns; consider HITL review");
            return ValidationResult.pass(); // 不阻断，由 HITL 机制处理
        }

        // 提取 SELECT 列并检查是否匹配敏感列
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

    // ==================== 校验结果类 ====================

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
     * 向后兼容：不带角色参数的校验方法。
     * 不进行敏感列检查（等同于管理员权限）。
     */
    public ValidationResult validate(String sql) {
        return validate(sql, null);
    }
}
