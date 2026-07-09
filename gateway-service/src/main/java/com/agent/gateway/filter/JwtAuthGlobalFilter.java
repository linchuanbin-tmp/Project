package com.agent.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @Value("${jwt.secret}")
    private String secret;

    // 白名单接口，直接放行
    private static final List<String> WHITELIST = Arrays.asList(
            "/api/user/login",
            "/api/user/register",
            "/ws"
    );

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. 白名单直接放行
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        // 2. 获取 Authorization Header
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return handleUnauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        try {
            // 3. 校验并解析 Token
            Claims claims = Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String username = claims.getSubject();
            String sessionKey = "session:active:" + username;

            // 4. Redis session check & renewal (Sliding Window)
            return redisTemplate.opsForValue().get(sessionKey)
                    .switchIfEmpty(Mono.defer(() -> Mono.error(new RuntimeException("Session expired due to inactivity."))))
                    .flatMap(activeToken -> {
                        if (!activeToken.equals(token)) {
                            return Mono.error(new RuntimeException("Session expired due to inactivity."));
                        }

                        // Sliding window: reset TTL on every non-polling request
                        boolean isBackgroundPoll = path != null && path.endsWith("/notification/unread-count");
                        if (!isBackgroundPoll) {
                            return redisTemplate.opsForValue().get("sys:config:session_timeout")
                                    .defaultIfEmpty("30")
                                    .flatMap(timeoutStr -> {
                                        long timeout = 30;
                                        try {
                                            timeout = Long.parseLong(timeoutStr);
                                        } catch (NumberFormatException ignored) {}
                                        return redisTemplate.expire(sessionKey, java.time.Duration.ofMinutes(timeout));
                                    })
                                    .then(proceedWithRequest(exchange, chain, username, claims));
                        }

                        return proceedWithRequest(exchange, chain, username, claims);
                    })
                    .onErrorResume(e -> handleUnauthorized(exchange, e.getMessage()));

        } catch (Exception e) {
            return handleUnauthorized(exchange, "Token validation failed: " + e.getMessage());
        }
    }

    private Mono<Void> proceedWithRequest(ServerWebExchange exchange, GatewayFilterChain chain, String username, Claims claims) {
        List<?> roles = claims.get("roles", List.class);
        List<?> permissions = claims.get("permissions", List.class);

        String rolesStr = roles != null ? StringUtils.collectionToCommaDelimitedString(roles) : "";
        String permsStr = permissions != null ? StringUtils.collectionToCommaDelimitedString(permissions) : "";

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Name", username)
                .header("X-User-Roles", rolesStr)
                .header("X-User-Permissions", permsStr)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isWhitelisted(String path) {
        for (String whitelistPath : WHITELIST) {
            if (path.equals(whitelistPath) || path.startsWith(whitelistPath + "/")) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> handleUnauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\": 401, \"message\": \"" + message + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100; // 较高优先级
    }
}
