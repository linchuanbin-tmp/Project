package com.agent.tool.controller;

import com.agent.tool.dto.*;
import com.agent.tool.entity.MeetingRoom;
import com.agent.tool.service.AmapService;
import com.agent.tool.service.MeetingRoomService;
import com.agent.tool.service.ScheduleService;
import com.agent.tool.service.ToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/tool")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ToolController {

    private final ToolService toolService;
    private final AmapService amapService;
    private final MeetingRoomService meetingRoomService;
    private final ScheduleService scheduleService;

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

    /**
     * 查询会议室（返回所有会议室，标记是否可预定）
     */
    @GetMapping("/meeting-rooms")
    public Result<List<Map<String, Object>>> getMeetingRooms(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false) Integer capacity) {

        if (startTime == null) startTime = LocalDateTime.now();
        if (endTime == null) endTime = startTime.plusHours(2);

        List<Map<String, Object>> rooms = meetingRoomService.queryRoomsWithStatus(startTime, endTime, capacity);
        return Result.success(rooms);
    }

    /**
     * 预定会议室
     */
    @PostMapping("/meeting-room/book")
    public Result<String> bookRoom(@RequestBody Map<String, Object> request) {
        Long roomId = Long.valueOf(request.get("roomId").toString());
        String booker = (String) request.get("booker");
        String startStr = (String) request.get("startTime");
        String endStr = (String) request.get("endTime");
        String topic = (String) request.getOrDefault("topic", "会议");

        LocalDateTime startTime = parseTime(startStr);
        LocalDateTime endTime = parseTime(endStr);

        boolean success = meetingRoomService.bookRoom(roomId, booker, startTime, endTime, topic);
        return success ? Result.success("预定成功") : Result.error("该会议室时段已被预定");
    }

    /**
     * 检查日程冲突
     */
    @PostMapping("/check-conflict")
    public Result<Map<String, Object>> checkScheduleConflict(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> attendees = (List<String>) request.get("attendees");
        String startTimeStr = (String) request.get("startTime");
        String endTimeStr = (String) request.get("endTime");

        LocalDateTime startTime = parseTime(startTimeStr);
        LocalDateTime endTime = endTimeStr != null ? parseTime(endTimeStr) : startTime.plusHours(1);

        boolean hasConflict = false;
        String conflictUser = null;

        if (attendees != null) {
            for (String userId : attendees) {
                if (scheduleService.checkConflict(userId, startTime, endTime)) {
                    hasConflict = true;
                    conflictUser = userId;
                    break;
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("hasConflict", hasConflict);
        result.put("conflictType", hasConflict ? "TIME_CONFLICT" : "NONE");
        result.put("conflictUser", hasConflict ? conflictUser : "");
        result.put("suggestedTime", hasConflict ? List.of() : List.of());
        result.put("message", hasConflict ? "用户 " + conflictUser + " 日程冲突" : "时段可用");
        return Result.success(result);
    }

    /**
     * 添加日程（同时写入数据库 + Redis）
     */
    @PostMapping("/schedule/create")
    public Result<String> createSchedule(@RequestBody ScheduleCreateRequest request) {
        // 1. 写入数据库（meeting_schedule 表，roomId=0 表示个人日程）
        meetingRoomService.addPersonalSchedule(request);

        // 2. 写入 Redis（用于快速冲突检测）
        scheduleService.addSchedule(
                request.getUserId(),
                request.getEventId(),
                request.getStartTime(),
                request.getEndTime()
        );

        return Result.success("日程添加成功");
    }

    @PostMapping("/schedule/add")
    public Result<String> addSchedule(@RequestBody ScheduleRequest request) {
        scheduleService.addSchedule(
                request.getUserId(),
                request.getEventId(),
                request.getStartTime(),
                request.getEndTime()
        );
        return Result.success("日程添加成功");
    }

    @PostMapping("/schedule/remove")
    public Result<String> removeSchedule(@RequestParam String userId, @RequestParam String eventId) {
        scheduleService.removeSchedule(userId, eventId);
        return Result.success("日程删除成功");
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

        Map<String, Object> result = new HashMap<>();
        result.put("from", from);
        result.put("to", to);
        result.put("distance", "12.5km");
        result.put("duration", "35分钟");
        result.put("trafficStatus", "畅通");
        result.put("steps", List.of(
                Map.of("instruction", "从起点出发", "distance", "500m"),
                Map.of("instruction", "进入主干道", "distance", "3.2km"),
                Map.of("instruction", "到达目的地", "distance", "200m")
        ));
        result.put("source", "mock");
        return Result.success(result);
    }

    private LocalDateTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return LocalDateTime.now();
        }
        timeStr = timeStr.replace("Z", "");
        if (timeStr.contains("T")) {
            return LocalDateTime.parse(timeStr);
        }
        return LocalDateTime.parse(timeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}