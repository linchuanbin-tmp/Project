package com.agent.user.filter;

import com.agent.user.entity.SysConfig;
import com.agent.user.mapper.SysConfigMapper;
import com.agent.user.utils.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final SysConfigMapper sysConfigMapper;

    private static final String SESSION_KEY_PREFIX        = "session:active:";
    private static final String REDIS_KEY_SESSION_TIMEOUT = "sys:config:session_timeout";
    private static final long   DEFAULT_SESSION_TIMEOUT   = 30L;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if ("/user/login".equals(uri) || "/user/register".equals(uri) || "/user/send-code".equals(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String jwt = parseJwt(request);
            if (jwt != null && jwtUtil.validateToken(jwt)) {
                Claims claims = jwtUtil.parseToken(jwt);
                String username = claims.getSubject();

                // ── Redis inactivity session check ──────────────────────────────
                String sessionKey  = SESSION_KEY_PREFIX + username;
                String activeToken = redisTemplate.opsForValue().get(sessionKey);

                if (activeToken == null || !activeToken.equals(jwt)) {
                    // Session expired due to inactivity, or token mismatch (another login kicked this one)
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"Session expired due to inactivity.\",\"data\":null}");
                    response.getWriter().flush();
                    return;
                }

                // ── Sliding window: reset TTL on every valid request ─────────────
                uri = request.getRequestURI();
                boolean isBackgroundPoll = uri != null && uri.endsWith("/notification/unread-count");

                if (!isBackgroundPoll) {
                    long timeoutMinutes = resolveSessionTimeout();
                    redisTemplate.expire(sessionKey, timeoutMinutes, TimeUnit.MINUTES);
                }

                // ── Build Security Context ───────────────────────────────────────
                List<?> rawRoles = claims.get("roles", List.class);
                List<String> roles = new ArrayList<>();
                if (rawRoles != null) {
                    for (Object r : rawRoles) roles.add(String.valueOf(r));
                }

                List<?> rawPermissions = claims.get("permissions", List.class);
                List<String> permissions = new ArrayList<>();
                if (rawPermissions != null) {
                    for (Object p : rawPermissions) permissions.add(String.valueOf(p));
                }

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                for (String role : roles) {
                    authorities.add(new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role));
                }
                for (String permission : permissions) {
                    authorities.add(new SimpleGrantedAuthority(permission));
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication: {}", e);
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }

    /**
     * Resolve session timeout minutes: Redis cache → DB → default (30).
     */
    private long resolveSessionTimeout() {
        String cached = redisTemplate.opsForValue().get(REDIS_KEY_SESSION_TIMEOUT);
        if (cached != null) {
            try { return Long.parseLong(cached); } catch (NumberFormatException ignored) {}
        }
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getParamKey, "session_timeout")
        );
        long value = DEFAULT_SESSION_TIMEOUT;
        if (config != null) {
            try { value = Long.parseLong(config.getParamValue()); } catch (NumberFormatException ignored) {}
        }
        redisTemplate.opsForValue().set(REDIS_KEY_SESSION_TIMEOUT, String.valueOf(value));
        return value;
    }
}
