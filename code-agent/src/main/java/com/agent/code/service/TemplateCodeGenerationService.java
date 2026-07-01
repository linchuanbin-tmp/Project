package com.agent.code.service;

import com.agent.code.dto.CodeGenerationRequest;
import com.agent.code.dto.CodeGenerationResponse;
import com.agent.code.entity.ColumnMetadata;
import com.agent.code.entity.TableMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板规则 SQL 生成服务
 * <p>
 * 在 ONNX 模型不可用时作为 fallback。
 * 基于关键词匹配 + 元数据上下文的规则引擎。
 * 支持常见查询模式：
 * - "查询所有 XXX" → SELECT * FROM xxx
 * - "查询 XXX 的 YYY" → SELECT yyy FROM xxx
 * - "统计 XXX 数量" → SELECT COUNT(*) FROM xxx
 * - "查询条件为 XXX=yyy 的记录" → SELECT * FROM xxx WHERE xxx = 'yyy'
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "code-agent.onnx.enabled", havingValue = "false", matchIfMissing = true)
public class TemplateCodeGenerationService implements CodeGenerationService {

    private final MetadataCacheService metadataCacheService;

    @Override
    public CodeGenerationResponse generateSQL(CodeGenerationRequest request) {
        String question = request.getQuestion();
        log.info("📝 模板引擎处理: {}", question);

        try {
            // 获取元数据上下文
            Map<String, TableMetadata> tables = metadataCacheService.getAllTableMetadata();

            // 匹配查询模式
            String sql = matchPattern(question, tables);

            log.info("📝 生成 SQL: {}", sql);
            return CodeGenerationResponse.builder()
                    .success(true)
                    .sql(sql)
                    .question(question)
                    .inferenceMethod("TEMPLATE")
                    .whitelistPassed(null) // Controller 层统一校验
                    .build();

        } catch (Exception e) {
            log.error("❌ 模板生成失败", e);
            return CodeGenerationResponse.builder()
                    .success(false)
                    .question(question)
                    .inferenceMethod("TEMPLATE")
                    .errorMessage("SQL 生成失败: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public String getInferenceMethod() {
        return "TEMPLATE";
    }

    // ==================== 模式匹配 ====================

    /**
     * 匹配查询模式并生成 SQL
     */
    private String matchPattern(String question, Map<String, TableMetadata> tables) {
        String q = question.trim();

        // 模式1: 统计数量 — "有多少XXX" / "XXX的数量" / "统计XXX"
        Pattern countPattern = Pattern.compile("(?:多少|几个|数量|统计|count)\\s*(?:个|张|条|笔)?\\s*(\\S+)");
        Matcher countMatcher = countPattern.matcher(q);
        if (countMatcher.find()) {
            String entity = countMatcher.group(1);
            String table = findBestTable(entity, tables);
            return String.format("SELECT COUNT(*) AS total FROM %s", table);
        }

        // 模式2: 查询全部 — "查询所有XXX" / "列出XXX" / "显示XXX"
        Pattern allPattern = Pattern.compile("(?:查询|列出|显示|查看|获取)\\s*(?:所有|全部)?\\s*(\\S+)");
        Matcher allMatcher = allPattern.matcher(q);
        if (allMatcher.find()) {
            String entity = allMatcher.group(1);
            String table = findBestTable(entity, tables);
            return String.format("SELECT * FROM %s", table);
        }

        // 模式3: 条件查询 — "查询XXX为YYY" / "XXX等于YYY"
        Pattern condPattern = Pattern.compile(
                "(?:查询|查找)\\s*(\\S+)\\s*(?:为|是|等于|=)\\s*['\"]?([^'\"\\s]+)['\"]?");
        Matcher condMatcher = condPattern.matcher(q);
        if (condMatcher.find()) {
            String field = condMatcher.group(1);
            String value = condMatcher.group(2);
            String table = findTableByField(field, tables);
            String column = findBestColumn(field, table, tables);
            return String.format("SELECT * FROM %s WHERE %s = '%s'", table, column, value);
        }

        // 模式4: 条件计数 — "XXX=YYY的有多少"
        Pattern condCountPattern = Pattern.compile(
                "(\\S+)\\s*(?:为|是|等于|=)\\s*['\"]?([^'\"\\s]+)['\"]?.*(?:多少|几个)");
        Matcher condCountMatcher = condCountPattern.matcher(q);
        if (condCountMatcher.find()) {
            String field = condCountMatcher.group(1);
            String value = condCountMatcher.group(2);
            String table = findTableByField(field, tables);
            String column = findBestColumn(field, table, tables);
            return String.format("SELECT COUNT(*) AS total FROM %s WHERE %s = '%s'", table, column, value);
        }

        // 模式5: 指定列查询 — "查看XXX的YYY和ZZZ"
        Pattern selectColsPattern = Pattern.compile(
                "(?:查看|查询|显示)\\s*(\\S+)\\s*(?:的|中)\\s*(.+)");
        Matcher selectColsMatcher = selectColsPattern.matcher(q);
        if (selectColsMatcher.find()) {
            String entity = selectColsMatcher.group(1);
            String columnsStr = selectColsMatcher.group(2);
            String table = findBestTable(entity, tables);
            List<String> columns = extractColumns(columnsStr, table, tables);
            String cols = String.join(", ", columns);
            return String.format("SELECT %s FROM %s", cols, table);
        }

        // 模式6: 聚合查询 — "XXX的总YYY" / "平均XXX" / "最大XXX"
        Pattern aggPattern = Pattern.compile(
                "(?:总|合计|平均|最大|最小|求和)\\s*(\\S+)|(\\S+)\\s*(?:的总和|的平均|的最大值|的最小值)");
        Matcher aggMatcher = aggPattern.matcher(q);
        if (aggMatcher.find()) {
            String entity = aggMatcher.group(1) != null ? aggMatcher.group(1) : aggMatcher.group(2);
            String table = findBestTable(entity, tables);
            String col = findAmountColumn(table, tables);
            if (q.contains("平均")) return String.format("SELECT AVG(%s) AS avg_value FROM %s", col, table);
            if (q.contains("最大")) return String.format("SELECT MAX(%s) AS max_value FROM %s", col, table);
            if (q.contains("最小")) return String.format("SELECT MIN(%s) AS min_value FROM %s", col, table);
            return String.format("SELECT SUM(%s) AS total FROM %s", col, table);
        }

        // 模式7: 排序查询 — "XXX最高的YYY" / "按XXX排序"
        Pattern orderPattern = Pattern.compile(
                "(?:最高|最低|最多|最少)\\s*(?:的)?|按\\s*(\\S+)\\s*(?:排序|排列)");
        Matcher orderMatcher = orderPattern.matcher(q);
        if (orderMatcher.find()) {
            String entity = orderMatcher.group(1) != null ? orderMatcher.group(1) : q;
            String table = findBestTable(entity, tables);
            String col = findAmountColumn(table, tables);
            String direction = q.contains("最低") || q.contains("最少") ? "ASC" : "DESC";
            return String.format("SELECT * FROM %s ORDER BY %s %s LIMIT 10", table, col, direction);
        }

        // 模式8: 日期范围 — "最近/过去N天的XXX" / "本月XXX" / "上个月XXX"
        Pattern datePattern = Pattern.compile(
                "(?:最近|过去)\\s*(\\d+)\\s*(?:天|日|周|月)");
        Matcher dateMatcher = datePattern.matcher(q);
        if (dateMatcher.find()) {
            int days = Integer.parseInt(dateMatcher.group(1));
            String table = findBestTable(q, tables);
            String dateCol = findDateColumn(table, tables);
            return String.format("SELECT * FROM %s WHERE %s >= DATE_SUB(NOW(), INTERVAL %d DAY) ORDER BY %s DESC",
                    table, dateCol, days, dateCol);
        }
        if (q.contains("本月") || q.contains("这个月")) {
            String table = findBestTable(q, tables);
            String dateCol = findDateColumn(table, tables);
            return String.format("SELECT * FROM %s WHERE YEAR(%s) = YEAR(NOW()) AND MONTH(%s) = MONTH(NOW())",
                    table, dateCol, dateCol);
        }
        if (q.contains("上个月") || q.contains("上月")) {
            String table = findBestTable(q, tables);
            String dateCol = findDateColumn(table, tables);
            return String.format("SELECT * FROM %s WHERE YEAR(%s) = YEAR(DATE_SUB(NOW(), INTERVAL 1 MONTH)) AND MONTH(%s) = MONTH(DATE_SUB(NOW(), INTERVAL 1 MONTH))",
                    table, dateCol, dateCol);
        }

        // 模式9: 分组查询 — "按XXX统计" / "每个XXX的"
        Pattern groupPattern = Pattern.compile(
                "按\\s*(\\S+)\\s*(?:统计|分组)|每个\\s*(\\S+)\\s*(?:的)");
        Matcher groupMatcher = groupPattern.matcher(q);
        if (groupMatcher.find()) {
            String groupField = groupMatcher.group(1) != null ? groupMatcher.group(1) : groupMatcher.group(2);
            String table = findBestTable(q, tables);
            String groupCol = findBestColumn(groupField, table, tables);
            String amountCol = findAmountColumn(table, tables);
            return String.format("SELECT %s, COUNT(*) AS cnt, SUM(%s) AS total FROM %s GROUP BY %s",
                    groupCol, amountCol, table, groupCol);
        }

        // 模式10: 模糊查询 — "名字包含XXX的"
        Pattern likePattern = Pattern.compile(
                "(?:名称|姓名|名字|备注|描述)\\s*(?:包含|含有|包括)\\s*['\"]?(\\S+)['\"]?");
        Matcher likeMatcher = likePattern.matcher(q);
        if (likeMatcher.find()) {
            String keyword = likeMatcher.group(1);
            String table = findBestTable(q, tables);
            String nameCol = findNameColumn(table, tables);
            return String.format("SELECT * FROM %s WHERE %s LIKE '%%%s%%'", table, nameCol, keyword);
        }

        // 默认: 尝试匹配最接近的表，返回 SELECT *
        String bestTable = findBestTable(q, tables);
        return String.format("SELECT * FROM %s LIMIT 100", bestTable);
    }

    // ==================== 实体-表名匹配 ====================

    /**
     * 根据中文描述找到最佳匹配的表
     */
    private String findBestTable(String entity, Map<String, TableMetadata> tables) {
        if (tables.isEmpty()) {
            return "unknown_table";
        }

        String lower = entity.toLowerCase();

        // 精确匹配表名
        for (String tableName : tables.keySet()) {
            if (tableName.equalsIgnoreCase(entity)) return tableName;
        }

        // 用关键词在表注释中搜索
        for (TableMetadata meta : tables.values()) {
            if (meta.getComment() != null && meta.getComment().contains(entity)) {
                return meta.getTableName();
            }
        }

        // 关键词映射（中文→表名，用 LinkedHashMap 保证顺序）
        Map<String, String> keywordMapping = new java.util.LinkedHashMap<>();
        keywordMapping.put("用户", "sys_user");
        keywordMapping.put("会议室", "meeting_room");
        keywordMapping.put("预定", "meeting_schedule");
        keywordMapping.put("日程", "meeting_schedule");
        keywordMapping.put("会议", "meeting_schedule");
        keywordMapping.put("客户", "bank_customer");
        keywordMapping.put("账户", "bank_account");
        keywordMapping.put("账号", "bank_account");
        keywordMapping.put("余额", "bank_account");
        keywordMapping.put("交易", "bank_transaction");
        keywordMapping.put("流水", "bank_transaction");
        keywordMapping.put("转账", "bank_transaction");
        keywordMapping.put("存款", "bank_transaction");

        for (Map.Entry<String, String> entry : keywordMapping.entrySet()) {
            if (lower.contains(entry.getKey()) && tables.containsKey(entry.getValue())) {
                return entry.getValue();
            }
        }

        // 模糊匹配
        for (String tableName : tables.keySet()) {
            if (tableName.toLowerCase().contains(lower) || lower.contains(tableName.toLowerCase())) {
                return tableName;
            }
        }

        // 返回第一张表
        return tables.keySet().iterator().next();
    }

    /**
     * 根据字段名找到所属表
     */
    private String findTableByField(String field, Map<String, TableMetadata> tables) {
        for (TableMetadata meta : tables.values()) {
            for (ColumnMetadata col : meta.getColumns()) {
                if (col.getColumnName().equalsIgnoreCase(field) ||
                        (col.getComment() != null && col.getComment().contains(field))) {
                    return meta.getTableName();
                }
            }
        }
        return tables.isEmpty() ? "unknown_table" : tables.keySet().iterator().next();
    }

    /**
     * 找到最佳匹配的列名
     */
    private String findBestColumn(String field, String table, Map<String, TableMetadata> tables) {
        TableMetadata meta = tables.get(table);
        if (meta == null) return field;

        // 精确匹配
        for (ColumnMetadata col : meta.getColumns()) {
            if (col.getColumnName().equalsIgnoreCase(field)) return col.getColumnName();
        }

        // 注释匹配
        for (ColumnMetadata col : meta.getColumns()) {
            if (col.getComment() != null && col.getComment().contains(field)) return col.getColumnName();
        }

        // 模糊匹配
        for (ColumnMetadata col : meta.getColumns()) {
            if (col.getColumnName().toLowerCase().contains(field.toLowerCase())) return col.getColumnName();
        }

        // 返回第一个字符串列
        for (ColumnMetadata col : meta.getColumns()) {
            if (col.getDataType().contains("char") || col.getDataType().contains("text")) {
                return col.getColumnName();
            }
        }

        return field;
    }

    /**
     * 从中文描述中提取列名
     */
    private List<String> extractColumns(String columnsStr, String table, Map<String, TableMetadata> tables) {
        List<String> result = new ArrayList<>();
        String[] parts = columnsStr.split("[、，,和及与]");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                String col = findBestColumn(trimmed, table, tables);
                if (!result.contains(col)) result.add(col);
            }
        }
        return result.isEmpty() ? List.of("*") : result;
    }

    /**
     * 查找金额类列（优先 balance, amount 等）
     */
    private String findAmountColumn(String table, Map<String, TableMetadata> tables) {
        TableMetadata meta = tables.get(table);
        if (meta == null) return "*";

        String[] candidates = {"balance", "amount", "total", "price", "money", "salary"};
        for (String candidate : candidates) {
            for (ColumnMetadata col : meta.getColumns()) {
                if (col.getColumnName().equalsIgnoreCase(candidate)) return col.getColumnName();
            }
        }
        // 返回第一个 decimal/numeric/int 列
        for (ColumnMetadata col : meta.getColumns()) {
            String type = col.getDataType().toLowerCase();
            if (type.contains("decimal") || type.contains("numeric") || type.contains("int"))
                return col.getColumnName();
        }
        return "*";
    }

    /**
     * 查找日期/时间列
     */
    private String findDateColumn(String table, Map<String, TableMetadata> tables) {
        TableMetadata meta = tables.get(table);
        if (meta == null) return "create_time";

        String[] candidates = {"txn_time", "create_time", "start_time", "end_time",
                "open_date", "date", "update_time", "time"};
        for (String candidate : candidates) {
            for (ColumnMetadata col : meta.getColumns()) {
                if (col.getColumnName().equalsIgnoreCase(candidate)) return col.getColumnName();
            }
        }
        // 返回第一个 date/datetime/time 类型列
        for (ColumnMetadata col : meta.getColumns()) {
            String type = col.getDataType().toLowerCase();
            if (type.contains("date") || type.contains("time")) return col.getColumnName();
        }
        return "create_time";
    }

    /**
     * 查找名称类列（优先 name, username 等）
     */
    private String findNameColumn(String table, Map<String, TableMetadata> tables) {
        TableMetadata meta = tables.get(table);
        if (meta == null) return "id";

        String[] candidates = {"name", "username", "real_name", "room_name",
                "topic", "remark", "customer_no", "account_no"};
        for (String candidate : candidates) {
            for (ColumnMetadata col : meta.getColumns()) {
                if (col.getColumnName().equalsIgnoreCase(candidate)) return col.getColumnName();
            }
        }
        // 返回第一个 varchar 列
        for (ColumnMetadata col : meta.getColumns()) {
            if (col.getDataType().toLowerCase().contains("char")) return col.getColumnName();
        }
        return "id";
    }
}
