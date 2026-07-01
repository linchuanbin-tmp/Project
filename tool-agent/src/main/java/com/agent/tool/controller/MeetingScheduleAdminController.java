package com.agent.tool.controller;

import com.agent.tool.dto.Result;
import com.agent.tool.entity.MeetingSchedule;
import com.agent.tool.service.MeetingScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/tool/admin/schedules")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MeetingScheduleAdminController {

    private final MeetingScheduleService meetingScheduleService;

    private void checkAdminRole(String roles) {
        if (!StringUtils.hasText(roles) || (!roles.contains("ROLE_ADMIN") && !roles.contains("ADMIN"))) {
            throw new RuntimeException("Unauthorized: Admin access required");
        }
    }

    @GetMapping
    public Result<List<MeetingSchedule>> listSchedules(
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        try {
            checkAdminRole(roles);
            return Result.success(meetingScheduleService.list());
        } catch (Exception e) {
            log.error("Failed to list schedules", e);
            return Result.error(e.getMessage());
        }
    }

    @PostMapping
    public Result<String> addSchedule(
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @RequestBody MeetingSchedule schedule) {
        try {
            checkAdminRole(roles);
            if (!StringUtils.hasText(schedule.getBooker())) {
                return Result.error("Booker name is required");
            }
            if (schedule.getRoomId() == null) {
                return Result.error("Room ID is required (0 for personal schedule)");
            }
            if (schedule.getStartTime() == null || schedule.getEndTime() == null) {
                return Result.error("Start and end times are required");
            }
            if (schedule.getStartTime().isAfter(schedule.getEndTime())) {
                return Result.error("Start time must be before end time");
            }
            schedule.setStatus(schedule.getStatus() == null ? 1 : schedule.getStatus());
            meetingScheduleService.save(schedule);
            return Result.success("Schedule created successfully");
        } catch (Exception e) {
            log.error("Failed to add schedule", e);
            return Result.error(e.getMessage());
        }
    }

    @PutMapping
    public Result<String> updateSchedule(
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @RequestBody MeetingSchedule schedule) {
        try {
            checkAdminRole(roles);
            if (schedule.getId() == null) {
                return Result.error("Schedule ID is required");
            }
            if (schedule.getStartTime() != null && schedule.getEndTime() != null && schedule.getStartTime().isAfter(schedule.getEndTime())) {
                return Result.error("Start time must be before end time");
            }
            meetingScheduleService.updateById(schedule);
            return Result.success("Schedule updated successfully");
        } catch (Exception e) {
            log.error("Failed to update schedule", e);
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteSchedule(
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @PathVariable Long id) {
        try {
            checkAdminRole(roles);
            meetingScheduleService.removeById(id);
            return Result.success("Schedule deleted successfully");
        } catch (Exception e) {
            log.error("Failed to delete schedule", e);
            return Result.error(e.getMessage());
        }
    }
}
