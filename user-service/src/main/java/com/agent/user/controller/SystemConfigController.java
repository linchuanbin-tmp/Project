package com.agent.user.controller;

import com.agent.user.dto.Result;
import com.agent.user.entity.SysConfig;
import com.agent.user.mapper.SysConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SysConfigMapper sysConfigMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String CONFIG_KEY_SESSION_TIMEOUT = "session_timeout";
    private static final String REDIS_KEY_SESSION_TIMEOUT  = "sys:config:session_timeout";
    private static final long   DEFAULT_SESSION_TIMEOUT    = 30L;

    /**
     * GET /user/config/session-timeout
     * Returns the current session inactivity timeout in minutes.
     * Admin-only.
     */
    @GetMapping("/session-timeout")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result<Long> getSessionTimeout() {
        long timeout = resolveTimeout();
        return Result.success(timeout);
    }

    /**
     * PUT /user/config/session-timeout
     * Updates the session inactivity timeout in minutes.
     * Admin-only.
     * Body: { "timeout": 45 }
     */
    @PutMapping("/session-timeout")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result<String> updateSessionTimeout(@RequestBody Map<String, Object> body) {
        Object raw = body.get("timeout");
        if (raw == null) {
            return Result.error(400, "Missing 'timeout' field in request body.");
        }

        long timeout;
        try {
            timeout = Long.parseLong(raw.toString());
        } catch (NumberFormatException e) {
            return Result.error(400, "Invalid 'timeout' value — must be a positive integer.");
        }

        if (timeout < 1 || timeout > 1440) {
            return Result.error(400, "Timeout must be between 1 and 1440 minutes.");
        }

        // Update database
        SysConfig existing = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getParamKey, CONFIG_KEY_SESSION_TIMEOUT)
        );
        if (existing == null) {
            SysConfig config = new SysConfig();
            config.setParamKey(CONFIG_KEY_SESSION_TIMEOUT);
            config.setParamValue(String.valueOf(timeout));
            config.setDescription("Session inactivity timeout in minutes");
            sysConfigMapper.insert(config);
        } else {
            sysConfigMapper.update(null,
                    new LambdaUpdateWrapper<SysConfig>()
                            .eq(SysConfig::getParamKey, CONFIG_KEY_SESSION_TIMEOUT)
                            .set(SysConfig::getParamValue, String.valueOf(timeout))
            );
        }

        // Refresh Redis cache
        stringRedisTemplate.opsForValue().set(REDIS_KEY_SESSION_TIMEOUT, String.valueOf(timeout));

        return Result.success("Session timeout updated to " + timeout + " minute(s).");
    }

    /**
     * Helper: resolve timeout value from Redis cache → DB → default (30).
     */
    private long resolveTimeout() {
        String cached = stringRedisTemplate.opsForValue().get(REDIS_KEY_SESSION_TIMEOUT);
        if (cached != null) {
            try { return Long.parseLong(cached); } catch (NumberFormatException ignored) {}
        }
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getParamKey, CONFIG_KEY_SESSION_TIMEOUT)
        );
        long value = DEFAULT_SESSION_TIMEOUT;
        if (config != null) {
            try { value = Long.parseLong(config.getParamValue()); } catch (NumberFormatException ignored) {}
        }
        stringRedisTemplate.opsForValue().set(REDIS_KEY_SESSION_TIMEOUT, String.valueOf(value));
        return value;
    }
}
