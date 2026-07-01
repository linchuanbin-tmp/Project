package com.agent.tool.controller;

import com.agent.tool.dto.Result;
import com.agent.tool.entity.MeetingRoom;
import com.agent.tool.service.MeetingRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/tool/admin/meeting-rooms")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MeetingRoomAdminController {

    private final MeetingRoomService meetingRoomService;

    private void checkAdminRole(String roles) {
        if (!StringUtils.hasText(roles) || (!roles.contains("ROLE_ADMIN") && !roles.contains("ADMIN"))) {
            throw new RuntimeException("Unauthorized: Admin access required");
        }
    }

    @GetMapping
    public Result<List<MeetingRoom>> listRooms(
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        try {
            checkAdminRole(roles);
            return Result.success(meetingRoomService.list());
        } catch (Exception e) {
            log.error("Failed to list meeting rooms", e);
            return Result.error(e.getMessage());
        }
    }

    @PostMapping
    public Result<String> addRoom(
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @RequestBody MeetingRoom room) {
        try {
            checkAdminRole(roles);
            if (!StringUtils.hasText(room.getRoomName())) {
                return Result.error("Room name is required");
            }
            if (room.getFloor() == null) {
                return Result.error("Floor is required");
            }
            if (room.getCapacity() == null || room.getCapacity() <= 0) {
                return Result.error("Capacity must be a positive integer");
            }
            room.setStatus(room.getStatus() == null ? 1 : room.getStatus());
            meetingRoomService.save(room);
            return Result.success("Meeting room created successfully");
        } catch (Exception e) {
            log.error("Failed to add meeting room", e);
            return Result.error(e.getMessage());
        }
    }

    @PutMapping
    public Result<String> updateRoom(
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @RequestBody MeetingRoom room) {
        try {
            checkAdminRole(roles);
            if (room.getId() == null) {
                return Result.error("Meeting room ID is required");
            }
            if (room.getCapacity() != null && room.getCapacity() <= 0) {
                return Result.error("Capacity must be a positive integer");
            }
            meetingRoomService.updateById(room);
            return Result.success("Meeting room updated successfully");
        } catch (Exception e) {
            log.error("Failed to update meeting room", e);
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteRoom(
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @PathVariable Long id) {
        try {
            checkAdminRole(roles);
            meetingRoomService.removeById(id);
            return Result.success("Meeting room deleted successfully");
        } catch (Exception e) {
            log.error("Failed to delete meeting room", e);
            return Result.error(e.getMessage());
        }
    }
}
