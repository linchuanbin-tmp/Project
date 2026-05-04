package com.agent.tool.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ToolRequest {
    private String toolType;
    private Map<String, Object> parameters;
    private String naturalLanguage;
}