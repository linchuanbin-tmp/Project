package com.agent.tool.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ToolResponse {
    private String toolType;
    private boolean success;
    private Map<String, Object> data;
    private String message;
    private String suggestion;
}