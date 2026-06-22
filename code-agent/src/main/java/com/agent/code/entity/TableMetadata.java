package com.agent.code.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 表元数据实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 表名 */
    private String tableName;

    /** 库名 */
    private String schemaName;

    /** 表注释 */
    private String comment;

    /** 列信息列表 */
    private List<ColumnMetadata> columns;
}
