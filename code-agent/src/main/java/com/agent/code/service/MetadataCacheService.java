package com.agent.code.service;

import com.agent.code.entity.ColumnMetadata;
import com.agent.code.entity.TableMetadata;
import com.agent.code.config.CodeAgentProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 元数据缓存服务
 * <p>
 * 从 MySQL information_schema 读取表结构，缓存到 Redis。
 * 用于：
 * 1. SQL 白名单校验（表名/列名校验）
 * 2. SQL 生成上下文（提供 schema 给 LLM 推理）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataCacheService {

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CodeAgentProperties properties;

    /**
     * 刷新所有表的元数据缓存
     */
    public Map<String, TableMetadata> refreshAllMetadata() {
        log.info("🔄 开始刷新元数据缓存...");
        Map<String, TableMetadata> metadataMap = loadFromInformationSchema();
        cacheToRedis(metadataMap);
        log.info("✅ 元数据缓存刷新完成，共 {} 张表", metadataMap.size());
        return metadataMap;
    }

    /**
     * 获取所有缓存的表元数据（优先 Redis，Redis 不可用时回退 MySQL）
     */
    public Map<String, TableMetadata> getAllTableMetadata() {
        String cacheKey = properties.getMetadata().getCachePrefix() + "tables";

        // 尝试从 Redis 读取
        try {
            Map<Object, Object> cached = redisTemplate.opsForHash().entries(cacheKey);
            if (!cached.isEmpty()) {
                log.debug("📦 从 Redis 读取元数据缓存，共 {} 张表", cached.size());
                return cached.entrySet().stream().collect(Collectors.toMap(
                        e -> (String) e.getKey(),
                        e -> {
                            try {
                                return objectMapper.readValue((String) e.getValue(), TableMetadata.class);
                            } catch (JsonProcessingException ex) {
                                throw new RuntimeException("元数据反序列化失败", ex);
                            }
                        }
                ));
            }
        } catch (Exception e) {
            log.debug("⚠️ Redis 不可用，直接从 MySQL 读取元数据: {}", e.getMessage());
        }

        // Redis 未命中或不可用，从 MySQL 加载
        log.info("⚠️ 从 MySQL 加载元数据（Redis 不可用或缓存为空）");
        return loadFromInformationSchema();
    }

    /**
     * 获取单表元数据
     */
    public Optional<TableMetadata> getTableMetadata(String tableName) {
        Map<String, TableMetadata> all = getAllTableMetadata();
        return Optional.ofNullable(all.get(tableName));
    }

    /**
     * 获取所有白名单表名
     */
    public Set<String> getAllowedTableNames() {
        return getAllTableMetadata().keySet();
    }

    /**
     * 获取指定表允许的列名
     */
    public Set<String> getAllowedColumnNames(String tableName) {
        return getTableMetadata(tableName)
                .map(t -> t.getColumns().stream()
                        .map(ColumnMetadata::getColumnName)
                        .collect(Collectors.toSet()))
                .orElse(Collections.emptySet());
    }

    // ==================== 私有方法 ====================

    /**
     * 从 MySQL information_schema 读取表结构
     */
    private Map<String, TableMetadata> loadFromInformationSchema() {
        Map<String, TableMetadata> metadataMap = new LinkedHashMap<>();

        try (Connection conn = dataSource.getConnection()) {
            String catalog = conn.getCatalog();

            // 获取所有用户表
            List<String> tableNames = queryTableNames(conn, catalog);
            log.info("📋 发现 {} 张用户表: {}", tableNames.size(), tableNames);

            for (String tableName : tableNames) {
                List<ColumnMetadata> columns = queryColumnMetadata(conn, catalog, tableName);
                String comment = queryTableComment(conn, catalog, tableName);

                TableMetadata tableMeta = TableMetadata.builder()
                        .tableName(tableName)
                        .schemaName(catalog)
                        .comment(comment)
                        .columns(columns)
                        .build();

                metadataMap.put(tableName, tableMeta);
            }
        } catch (SQLException e) {
            log.error("❌ 读取 information_schema 失败", e);
            throw new RuntimeException("元数据加载失败", e);
        }

        return metadataMap;
    }

    /**
     * 查询所有用户表名（排除系统表）
     */
    private List<String> queryTableNames(Connection conn, String catalog) throws SQLException {
        String sql = """
                SELECT TABLE_NAME
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ?
                  AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME
                """;
        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, catalog);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("TABLE_NAME"));
                }
            }
        }
        return names;
    }

    /**
     * 查询表的所有列信息
     */
    private List<ColumnMetadata> queryColumnMetadata(Connection conn, String catalog, String tableName)
            throws SQLException {
        String sql = """
                SELECT c.COLUMN_NAME,
                       c.DATA_TYPE,
                       c.IS_NULLABLE,
                       c.COLUMN_COMMENT,
                       c.COLUMN_KEY
                FROM information_schema.COLUMNS c
                WHERE c.TABLE_SCHEMA = ?
                  AND c.TABLE_NAME = ?
                ORDER BY c.ORDINAL_POSITION
                """;
        List<ColumnMetadata> columns = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, catalog);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ColumnMetadata col = ColumnMetadata.builder()
                            .columnName(rs.getString("COLUMN_NAME"))
                            .dataType(rs.getString("DATA_TYPE"))
                            .nullable("YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")))
                            .comment(rs.getString("COLUMN_COMMENT"))
                            .isPrimaryKey("PRI".equalsIgnoreCase(rs.getString("COLUMN_KEY")))
                            .build();
                    columns.add(col);
                }
            }
        }
        return columns;
    }

    /**
     * 查询表注释
     */
    private String queryTableComment(Connection conn, String catalog, String tableName) throws SQLException {
        String sql = """
                SELECT TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ?
                  AND TABLE_NAME = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, catalog);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("TABLE_COMMENT");
                }
            }
        }
        return "";
    }

    /**
     * 缓存到 Redis（失败不抛异常，仅记日志）
     */
    private void cacheToRedis(Map<String, TableMetadata> metadataMap) {
        try {
            String cacheKey = properties.getMetadata().getCachePrefix() + "tables";
            int ttl = properties.getMetadata().getCacheTtl();

            Map<String, String> hashData = new HashMap<>();
            for (Map.Entry<String, TableMetadata> entry : metadataMap.entrySet()) {
                hashData.put(entry.getKey(), objectMapper.writeValueAsString(entry.getValue()));
            }

            redisTemplate.delete(cacheKey);
            if (!hashData.isEmpty()) {
                redisTemplate.opsForHash().putAll(cacheKey, hashData);
                redisTemplate.expire(cacheKey, ttl, TimeUnit.SECONDS);
            }
            log.info("💾 元数据已缓存到 Redis (key={}, ttl={}s)", cacheKey, ttl);
        } catch (Exception e) {
            log.warn("⚠️ 元数据缓存到 Redis 失败（Redis 不可用）: {}", e.getMessage());
        }
    }
}
