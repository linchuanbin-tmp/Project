package com.agent.tool.controller;

import com.agent.tool.dto.Result;
import com.agent.tool.dto.ToolRequest;
import com.agent.tool.dto.ToolResponse;
import com.agent.tool.service.AmapService;
import com.agent.tool.service.ToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/tool")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ToolController {

    private final ToolService toolService;
    private final AmapService amapService;

    @PostMapping("/execute")
    public Result<Map<String, Object>> executeTool(@RequestBody ToolRequest request) {
        try {
            ToolResponse response = toolService.execute(request);
            if (response.isSuccess()) {
                return Result.success(response.getData());
            } else {
                log.warn("工具执行返回失败: {}", response.getMessage());
                return Result.error(response.getMessage());
            }
        } catch (Exception e) {
            log.error("执行工具异常", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/meeting-rooms")
    public Result<List<Map<String, Object>>> getMeetingRooms(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Integer capacity) {
        List<Map<String, Object>> rooms = List.of(
                Map.of("id", "A-101", "name", "第一会议室", "capacity", 20,
                        "location", "1号楼3层", "equipment", List.of("投影仪", "白板"),
                        "available", true, "nextBooking", "14:00-16:00"),
                Map.of("id", "A-102", "name", "第二会议室", "capacity", 10,
                        "location", "1号楼3层", "equipment", List.of("白板"),
                        "available", true, "nextBooking", null)
        );
        return Result.success(rooms);
    }

    @PostMapping("/check-conflict")
    public Result<Map<String, Object>> checkScheduleConflict(
            @RequestBody Map<String, Object> request) {
        String startTime = (String) request.get("startTime");
        @SuppressWarnings("unchecked")
        List<String> attendees = (List<String>) request.get("attendees");

        boolean hasConflict = attendees != null && attendees.contains("admin")
                && startTime != null && startTime.startsWith("2026-05-01 10:");

        return Result.success(Map.of(
                "hasConflict", hasConflict,
                "conflictType", hasConflict ? "TIME_CONFLICT" : "NONE",
                "suggestedTime", hasConflict ? List.of("2026-05-01 14:00-16:00") : List.of(),
                "message", hasConflict ? "检测到冲突" : "时段可用"
        ));
    }

    @GetMapping("/route")
    public Result<Map<String, Object>> planRoute(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "driving") String mode) {

        if (amapService != null) {
            try {
                return Result.success(amapService.planDrivingRoute(from, to, 0));
            } catch (Exception e) {
                log.warn("高德路线规划失败，使用Mock数据", e);
            }
        }

        return Result.success(Map.of(
                "from", from,
                "to", to,
                "distance", "12.5km",
                "duration", "35分钟",
                "trafficStatus", "畅通",
                "steps", List.of(
                        Map.of("instruction", "从起点出发", "distance", "500m"),
                        Map.of("instruction", "进入主干道", "distance", "3.2km"),
                        Map.of("instruction", "到达目的地", "distance", "200m")
                ),
                "source", "mock"
        ));
    }
}