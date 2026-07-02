package com.agent.tool.service.impl;

import com.agent.tool.dto.ScheduleCreateRequest;
import com.agent.tool.entity.MeetingRoom;
import com.agent.tool.entity.MeetingSchedule;
import com.agent.tool.mapper.MeetingRoomMapper;
import com.agent.tool.mapper.MeetingScheduleMapper;
import com.agent.tool.service.MeetingRoomService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetingRoomServiceImpl extends ServiceImpl<MeetingRoomMapper, MeetingRoom> implements MeetingRoomService {

    private final MeetingScheduleMapper scheduleMapper;

    @Override
    public List<Map<String, Object>> queryRoomsWithStatus(LocalDateTime startTime, LocalDateTime endTime, Integer minCapacity) {
        // 1. 查所有会议室
        LambdaQueryWrapper<MeetingRoom> roomQuery = new LambdaQueryWrapper<>();
        roomQuery.eq(MeetingRoom::getStatus, 1);
        if (minCapacity != null) {
            roomQuery.ge(MeetingRoom::getCapacity, minCapacity);
        }
        List<MeetingRoom> allRooms = list(roomQuery);

        // 2. 查该时间段所有预订
        LambdaQueryWrapper<MeetingSchedule> scheduleQuery = new LambdaQueryWrapper<>();
        scheduleQuery.eq(MeetingSchedule::getStatus, 1)
                .lt(MeetingSchedule::getStartTime, endTime)
                .gt(MeetingSchedule::getEndTime, startTime);
        List<MeetingSchedule> bookings = scheduleMapper.selectList(scheduleQuery);

        Set<Long> bookedRoomIds = bookings.stream()
                .map(MeetingSchedule::getRoomId)
                .collect(Collectors.toSet());

        // 3. 组装结果
        List<Map<String, Object>> result = new ArrayList<>();
        for (MeetingRoom room : allRooms) {
            boolean isBooked = bookedRoomIds.contains(room.getId());
            List<String> equipment = room.getFacilities() != null
                    ? List.of(room.getFacilities().split(","))
                    : List.of();

            Map<String, Object> map = new HashMap<>();
            map.put("id", room.getId().toString());
            map.put("name", room.getRoomName());
            map.put("capacity", room.getCapacity());
            String building = room.getBuilding();
            String floor = room.getFloor();
            String location = (building != null && !building.trim().isEmpty() ? building + ", " : "") + "Floor " + floor;
            map.put("location", location);
            map.put("equipment", equipment);
            map.put("available", !isBooked);
            map.put("statusText", isBooked ? "Booked" : "Available");
            map.put("nextBooking", isBooked ? "This time slot is already booked" : "");
            result.add(map);
        }
        return result;
    }

    @Override
    public boolean bookRoom(Long roomId, String booker, LocalDateTime startTime, LocalDateTime endTime, String topic) {
        // 检查是否冲突
        LambdaQueryWrapper<MeetingSchedule> query = new LambdaQueryWrapper<>();
        query.eq(MeetingSchedule::getRoomId, roomId)
                .eq(MeetingSchedule::getStatus, 1)
                .lt(MeetingSchedule::getStartTime, endTime)
                .gt(MeetingSchedule::getEndTime, startTime);
        if (scheduleMapper.selectCount(query) > 0) {
            return false;
        }

        MeetingSchedule schedule = new MeetingSchedule();
        schedule.setRoomId(roomId);
        schedule.setBooker(booker);
        schedule.setStartTime(startTime);
        schedule.setEndTime(endTime);
        schedule.setTopic(topic);
        schedule.setStatus(1);
        scheduleMapper.insert(schedule);
        return true;
    }

    @Override
    public MeetingSchedule addPersonalSchedule(ScheduleCreateRequest request) {
        MeetingSchedule schedule = new MeetingSchedule();
        schedule.setRoomId(0L); // 0 表示个人日程，无会议室
        schedule.setBooker(request.getUserId());
        schedule.setStartTime(request.getStartTime());
        schedule.setEndTime(request.getEndTime());
        schedule.setTopic(request.getEventName());
        schedule.setStatus(1);
        scheduleMapper.insert(schedule);
        return schedule;
    }

    @Override
    public List<Map<String, Object>> getSchedulesForUsers(List<String> users, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<MeetingSchedule> query = new LambdaQueryWrapper<>();
        query.eq(MeetingSchedule::getStatus, 1);
        if (users != null && !users.isEmpty()) {
            query.in(MeetingSchedule::getBooker, users);
        }
        query.ge(MeetingSchedule::getEndTime, startTime);
        query.le(MeetingSchedule::getStartTime, endTime);
        query.orderByAsc(MeetingSchedule::getStartTime);

        List<MeetingSchedule> list = scheduleMapper.selectList(query);
        List<Map<String, Object>> result = new ArrayList<>();
        for (MeetingSchedule schedule : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", schedule.getId());
            map.put("booker", schedule.getBooker());
            map.put("startTime", schedule.getStartTime());
            map.put("endTime", schedule.getEndTime());
            map.put("topic", schedule.getTopic());
            map.put("roomId", schedule.getRoomId());
            result.add(map);
        }
        return result;
    }
}