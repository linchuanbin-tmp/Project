package com.agent.tool.service;

import com.agent.tool.dto.ToolRequest;
import com.agent.tool.dto.ToolResponse;

public interface ToolService {
    ToolResponse execute(ToolRequest request);
}