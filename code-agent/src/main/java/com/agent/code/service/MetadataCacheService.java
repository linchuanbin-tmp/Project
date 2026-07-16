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
 * Metadata cache service
 * <p>
 * Read table structure from MySQL information_schema and cache to Redis.
 * Used for:
 * 1. SQL whitelist validation (table/column name validation)
 * 2. SQL generation context (provide schema to LLM for inference)
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
     * Refresh metadata cache for all tables
     */
    public Map<String, TableMetadata> refreshAllMetadata() {
        log.info("🔄 开始刷新元数据缓存...");
        Map<String, TableMetadata> metadataMap = loadFromInformationSchema();
        cacheToRedis(metadataMap);
        log.info("✅ 元数据缓存刷新完成，共 {} 张表", metadataMap.size());
        return metadataMap;
    }

    /**
     * Get all cached table metadata (Redis first, fallback to MySQL when Redis is unavailable)
     */
    public Map<String, TableMetadata> getAllTableMetadata() {
        String cacheKey = properties.getMetadata().getCachePrefix() + "tables";

        // Try reading from Redis
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

        // Redis miss or unavailable, load from MySQL
        log.info("⚠️ 从 MySQL 加载元数据（Redis 不可用或缓存为空）");
        return loadFromInformationSchema();
    }

    /**
     * Get metadata for a single table
     */
    public Optional<TableMetadata> getTableMetadata(String tableName) {
        Map<String, TableMetadata> all = getAllTableMetadata();
        return Optional.ofNullable(all.get(tableName));
    }

    /**
     * Get all whitelisted table names
     */
    public Set<String> getAllowedTableNames() {
        return getAllTableMetadata().keySet();
    }

    /**
     * Get allowed column names for a specific table
     */
    public Set<String> getAllowedColumnNames(String tableName) {
        return getTableMetadata(tableName)
                .map(t -> t.getColumns().stream()
                        .map(ColumnMetadata::getColumnName)
                        .collect(Collectors.toSet()))
                .orElse(Collections.emptySet());
    }

    // ==================== Private Methods ====================

    /**
     * Read table structure from MySQL information_schema
     */
    private Map<String, TableMetadata> loadFromInformationSchema() {
        Map<String, TableMetadata> metadataMap = new LinkedHashMap<>();

        try (Connection conn = dataSource.getConnection()) {
            String catalog = conn.getCatalog();

            // Get all user tables
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
     * Query all user table names (exclude system tables)
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
     * Query all column information for a table
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
     * Query table comment
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
     * Cache to Redis (no exception on failure, log only)
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
