package com.agent.code.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 元数据缓存查询响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataCacheResponse {

    /** 缓存的表数量 */
    private Integer tableCount;

    /** 表名列表 */
    private List<String> tableNames;

    /** 缓存来源 */
    private String source;

    /** 查询时间戳 */
    private Long timestamp;
}
