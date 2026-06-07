package com.agent.tool.service.impl;

import com.agent.tool.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String PREFIX = "tool:schedule:";

    @Override
    public boolean checkConflict(String userId, LocalDateTime startTime, LocalDateTime endTime) {
        String key = PREFIX + userId;
        long startTs = startTime.toEpochSecond(ZoneOffset.ofHours(8));
        long endTs = endTime.toEpochSecond(ZoneOffset.ofHours(8));

        // 查询该用户在 [startTs, endTs] 内是否有日程
        Set<String> events = redisTemplate.opsForZSet().rangeByScore(key, startTs, endTs);
        return events != null && !events.isEmpty();
    }

    @Override
    public void addSchedule(String userId, String eventId, LocalDateTime startTime, LocalDateTime endTime) {
        String key = PREFIX + userId;
        long startTs = startTime.toEpochSecond(ZoneOffset.ofHours(8));
        long endTs = endTime.toEpochSecond(ZoneOffset.ofHours(8));
        String value = eventId + "|" + startTs + "|" + endTs;
        redisTemplate.opsForZSet().add(key, value, startTs);
    }

    @Override
    public void removeSchedule(String userId, String eventId) {
        String key = PREFIX + userId;
        Set<String> members = redisTemplate.opsForZSet().range(key, 0, -1);
        if (members != null) {
            for (String member : members) {
                if (member.startsWith(eventId + "|")) {
                    redisTemplate.opsForZSet().remove(key, member);
                }
            }
        }
    }
}