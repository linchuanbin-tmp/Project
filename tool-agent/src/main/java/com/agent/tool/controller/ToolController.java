package com.agent.tool.controller;

import com.agent.tool.dto.*;
import com.agent.tool.entity.MeetingRoom;
import com.agent.tool.entity.MeetingSchedule;
import com.agent.tool.service.AmapService;
import com.agent.tool.service.MeetingRoomService;
import com.agent.tool.service.MeetingScheduleService;
import com.agent.tool.service.ScheduleService;
import com.agent.tool.service.ToolService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.ArrayList;

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
    private final MeetingScheduleService meetingScheduleService;
    private final JdbcTemplate jdbcTemplate;

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
     * Query meeting rooms (return all rooms, mark whether bookable).
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
     * Book a meeting room.
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
        if (success) {
            try {
                String roomName = "Unknown Room";
                MeetingRoom room = meetingRoomService.getById(roomId);
                if (room != null) {
                    roomName = room.getRoomName();
                }
                String title = "Meeting Room Reserved";
                String content = String.format("You have successfully reserved %s for topic: \"%s\" from %s to %s.",
                    roomName, topic, startStr, endStr);
                String payload = String.format("{\"roomId\":%d,\"roomName\":\"%s\",\"startTime\":\"%s\",\"endTime\":\"%s\",\"topic\":\"%s\"}",
                    roomId, roomName, startStr, endStr, topic);
                sendSystemNotification(booker, title, content, "MEETING", payload);
            } catch (Exception e) {
                log.error("Failed to send booking notification", e);
            }
            return Result.success("预定成功");
        } else {
            return Result.error("该会议室时段已被预定");
        }
    }

    /**
     * Check schedule conflicts.
     */
    @PostMapping("/check-conflict")
    public Result<Map<String, Object>> checkScheduleConflict(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> attendees = (List<String>) request.get("attendees");
        String startTimeStr = (String) request.get("startTime");
        String endTimeStr = (String) request.get("endTime");

        if (attendees == null || attendees.isEmpty()) {
            return Result.error("Please select at least one attendee to check conflict.");
        }

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
        result.put("message", hasConflict ? "User " + conflictUser + " has a schedule conflict" : "All attendees are available during this time slot");
        return Result.success(result);
    }

    /**
     * Add a schedule (write to both database and Redis).
     */
    @PostMapping("/schedule/create")
    public Result<String> createSchedule(@RequestBody ScheduleCreateRequest request) {
        // Step 1. Write to database (meeting_schedule table, roomId=0 means personal schedule) and return object with primary key
        MeetingSchedule schedule = meetingRoomService.addPersonalSchedule(request);

        // Step 2. Write to Redis (for fast conflict detection), using event_ + id as unique identifier
        scheduleService.addSchedule(
                request.getUserId(),
                "event_" + schedule.getId(),
                request.getStartTime(),
                request.getEndTime()
        );

        return Result.success("Schedule added successfully");
    }

    @PostMapping("/schedule/add")
    public Result<String> addSchedule(@RequestBody ScheduleRequest request) {
        scheduleService.addSchedule(
                request.getUserId(),
                request.getEventId(),
                request.getStartTime(),
                request.getEndTime()
        );
        return Result.success("Schedule added successfully");
    }

    @PostMapping("/schedule/remove")
    public Result<String> removeSchedule(@RequestParam String userId, @RequestParam String eventId) {
        scheduleService.removeSchedule(userId, eventId);
        return Result.success("Schedule deleted successfully");
    }

    @GetMapping("/schedules")
    public Result<List<Map<String, Object>>> getSchedules(
            @RequestParam String date,
            @RequestParam(required = false) List<String> users) {
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(date);
            LocalDateTime startOfDay = d.atStartOfDay();
            LocalDateTime endOfDay = d.atTime(23, 59, 59);

            List<Map<String, Object>> list = meetingRoomService.getSchedulesForUsers(users, startOfDay, endOfDay);
            return Result.success(list);
        } catch (Exception e) {
            log.error("Failed to query schedules", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/my-schedules")
    public Result<List<Map<String, Object>>> getMySchedules(
            @RequestHeader(value = "X-User-Name", required = true) String username) {
        try {
            LambdaQueryWrapper<MeetingSchedule> query = new LambdaQueryWrapper<>();
            query.eq(MeetingSchedule::getBooker, username)
                 .eq(MeetingSchedule::getStatus, 1)
                 .orderByDesc(MeetingSchedule::getStartTime);
            List<MeetingSchedule> list = meetingScheduleService.list(query);
            List<Map<String, Object>> result = new ArrayList<>();
            for (MeetingSchedule schedule : list) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", schedule.getId());
                map.put("booker", schedule.getBooker());
                map.put("startTime", schedule.getStartTime());
                map.put("endTime", schedule.getEndTime());
                map.put("topic", schedule.getTopic());
                map.put("roomId", schedule.getRoomId());
                
                if (schedule.getRoomId() != null && schedule.getRoomId() > 0) {
                    MeetingRoom room = meetingRoomService.getById(schedule.getRoomId());
                    if (room != null) {
                        map.put("roomName", room.getRoomName());
                        map.put("location", (room.getBuilding() != null && !room.getBuilding().trim().isEmpty() ? room.getBuilding() + ", " : "") + "Floor " + room.getFloor());
                    } else {
                        map.put("roomName", "Room " + schedule.getRoomId());
                        map.put("location", "Unknown Location");
                    }
                } else {
                    map.put("roomName", "Personal Schedule");
                    map.put("location", "N/A");
                }
                result.add(map);
            }
            return Result.success(result);
        } catch (Exception e) {
            log.error("Failed to query user schedules", e);
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/my-schedule/{id}")
    public Result<String> deleteMySchedule(
            @RequestHeader(value = "X-User-Name", required = true) String username,
            @PathVariable Long id) {
        try {
            MeetingSchedule schedule = meetingScheduleService.getById(id);
            if (schedule == null) {
                return Result.error("Schedule not found");
            }
            if (!username.equals(schedule.getBooker())) {
                return Result.error("Permission denied: You do not own this schedule");
            }
            
            // Set status to 0 to cancel
            schedule.setStatus(0);
            meetingScheduleService.updateById(schedule);
            
            // If personal schedule, remove from Redis
            if (schedule.getRoomId() == null || schedule.getRoomId() == 0) {
                scheduleService.removeSchedule(username, "event_" + id);
            }

            try {
                String roomName = "Personal Schedule";
                if (schedule.getRoomId() != null && schedule.getRoomId() > 0) {
                    MeetingRoom room = meetingRoomService.getById(schedule.getRoomId());
                    if (room != null) {
                        roomName = room.getRoomName();
                    } else {
                        roomName = "Room " + schedule.getRoomId();
                    }
                }
                String title = "Meeting Room Reservation Cancelled";
                String content = String.format("Your reservation for %s (Topic: \"%s\") on %s to %s has been cancelled.",
                    roomName, schedule.getTopic(), schedule.getStartTime(), schedule.getEndTime());
                String payload = String.format("{\"roomId\":%d,\"roomName\":\"%s\",\"startTime\":\"%s\",\"endTime\":\"%s\",\"topic\":\"%s\"}",
                    schedule.getRoomId(), roomName, schedule.getStartTime(), schedule.getEndTime(), schedule.getTopic());
                sendSystemNotification(username, title, content, "MEETING", payload);
            } catch (Exception e) {
                log.error("Failed to send cancellation notification", e);
            }
            
            return Result.success("Schedule cancelled successfully");
        } catch (Exception e) {
            log.error("Failed to cancel schedule", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/route")
    public Result<Map<String, Object>> planRoute(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "driving") String mode) {

        if (amapService != null) {
            try {
                Map<String, Object> result;
                switch (mode) {
                    case "transit":
                        result = amapService.planTransitRoute(from, to);
                        break;
                    case "walking":
                        result = amapService.planWalkingRoute(from, to);
                        break;
                    default:
                        result = amapService.planDrivingRoute(from, to, 0);
                        break;
                }
                return Result.success(result);
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

    private void sendSystemNotification(String username, String title, String content, String notifyType, String payload) {
        try {
            Long userId = null;
            try {
                userId = jdbcTemplate.queryForObject(
                    "SELECT id FROM sys_user WHERE username = ? AND deleted = 0",
                    Long.class,
                    username
                );
            } catch (Exception e) {
                log.warn("User not found in sys_user for username: {}, skipping notification", username);
                return;
            }

            if (userId == null) {
                log.warn("User not found for username: {}, skipping notification", username);
                return;
            }
            
            jdbcTemplate.update(
                "INSERT INTO sys_notification (sender_id, receiver_id, title, content, notify_type, status, payload) VALUES (?, ?, ?, ?, ?, ?, ?)",
                1L,        // senderId = 1 (System / Admin)
                userId,    // receiverId
                title,     // title
                content,   // content
                notifyType,// notifyType
                1,         // status = 1 (Unread)
                payload    // payload JSON
            );
        } catch (Exception e) {
            log.error("Failed to insert system notification", e);
        }
    }
}