package com.agent.code.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Metadata cache query response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataCacheResponse {

    /** Number of cached tables */
    private Integer tableCount;

    /** List of table names */
    private List<String> tableNames;

    /** Cache source */
    private String source;

    /** Query timestamp */
    private Long timestamp;
}
