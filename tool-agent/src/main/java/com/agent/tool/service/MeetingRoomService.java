package com.agent.tool.service;

import com.agent.tool.dto.ScheduleCreateRequest;
import com.agent.tool.entity.MeetingRoom;
import com.agent.tool.entity.MeetingSchedule;
import com.baomidou.mybatisplus.extension.service.IService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface MeetingRoomService extends IService<MeetingRoom> {
// 查询所有会议室，并标记是否可预定
List<Map<String, Object>> queryRoomsWithStatus(LocalDateTime startTime, LocalDateTime endTime, Integer minCapacity);

// 预定会议室
boolean bookRoom(Long roomId, String booker, LocalDateTime startTime, LocalDateTime endTime, String topic);

// 添加个人日程（写入数据库）
MeetingSchedule addPersonalSchedule(ScheduleCreateRequest request);

// 查询指定人在时间段内的日程
List<Map<String, Object>> getSchedulesForUsers(List<String> users, LocalDateTime startTime, LocalDateTime endTime);
}