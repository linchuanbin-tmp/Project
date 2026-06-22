package com.agent.code.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 列元数据实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 列名 */
    private String columnName;

    /** 数据类型 (varchar, int, datetime...) */
    private String dataType;

    /** 是否可为空 */
    private Boolean nullable;

    /** 列注释 */
    private String comment;

    /** 是否是主键 */
    private Boolean isPrimaryKey;
}
