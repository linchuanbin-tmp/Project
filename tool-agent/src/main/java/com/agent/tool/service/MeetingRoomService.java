package com.agent.tool.service;

import com.agent.tool.dto.ScheduleCreateRequest;
import com.agent.tool.entity.MeetingRoom;
import com.agent.tool.entity.MeetingSchedule;
import com.baomidou.mybatisplus.extension.service.IService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface MeetingRoomService extends IService<MeetingRoom> {
// Query all meeting rooms and mark bookability
List<Map<String, Object>> queryRoomsWithStatus(LocalDateTime startTime, LocalDateTime endTime, Integer minCapacity);

// Book a meeting room
boolean bookRoom(Long roomId, String booker, LocalDateTime startTime, LocalDateTime endTime, String topic);

// Add personal schedule (write to database)
MeetingSchedule addPersonalSchedule(ScheduleCreateRequest request);

// Query schedules for specified users within a time range
List<Map<String, Object>> getSchedulesForUsers(List<String> users, LocalDateTime startTime, LocalDateTime endTime);
}