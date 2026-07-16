package com.agent.code.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Table metadata entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Table name */
    private String tableName;

    /** Schema name */
    private String schemaName;

    /** Table comment */
    private String comment;

    /** Column information list */
    private List<ColumnMetadata> columns;
}
