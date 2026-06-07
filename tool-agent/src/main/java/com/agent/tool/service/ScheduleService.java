package com.agent.tool.service;

import java.time.LocalDateTime;

public interface ScheduleService {
    boolean checkConflict(String userId, LocalDateTime startTime, LocalDateTime endTime);
    void addSchedule(String userId, String eventId, LocalDateTime startTime, LocalDateTime endTime);
    void removeSchedule(String userId, String eventId);
}