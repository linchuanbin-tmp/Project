package com.agent.code.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Column metadata entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Column name */
    private String columnName;

    /** Data type (varchar, int, datetime...) */
    private String dataType;

    /** Whether nullable */
    private Boolean nullable;

    /** Column comment */
    private String comment;

    /** Whether primary key */
    private Boolean isPrimaryKey;
}
