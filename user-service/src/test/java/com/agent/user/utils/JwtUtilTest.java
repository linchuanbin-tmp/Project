package com.agent.user.utils;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JWT 工具类")
class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil();

    @BeforeEach
    void setUp() {
        // Inject fixed secret and expiration (bypass @Value dependency on Spring context)
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-for-unit-test-at-least-256-bits-long!");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3600000L); // 1 hour
    }

    @Nested
    @DisplayName("Token 生成")
    class TokenGeneration {

        @Test
        @DisplayName("应生成非空 token")
        void shouldGenerateToken() {
            String token = jwtUtil.generateToken("admin", List.of("ROLE_ADMIN"), List.of("user:read"));
            assertThat(token).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("生成的 token 应通过 validate")
        void shouldValidateOwnToken() {
            String token = jwtUtil.generateToken("admin", List.of("ROLE_ADMIN"), List.of("user:read"));
            assertThat(jwtUtil.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("错误密钥的 token 不应通过 validate")
        void shouldRejectWrongSignature() {
            // Use a different secret
            JwtUtil otherJwt = new JwtUtil();
            ReflectionTestUtils.setField(otherJwt, "secret", "different-secret-key-for-testing-purposes-only!");
            ReflectionTestUtils.setField(otherJwt, "expiration", 3600000L);
            String token = otherJwt.generateToken("admin", List.of("ROLE_ADMIN"), List.of());
            assertThat(jwtUtil.validateToken(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("Token 解析")
    class TokenParsing {

        @Test
        @DisplayName("应正确解析 username")
        void shouldParseUsername() {
            String token = jwtUtil.generateToken("zhangsan", List.of("ROLE_USER"), List.of());
            assertThat(jwtUtil.getUsernameFromToken(token)).isEqualTo("zhangsan");
        }

        @Test
        @DisplayName("应正确解析 roles")
        void shouldParseRoles() {
            String token = jwtUtil.generateToken("admin", List.of("ROLE_ADMIN", "ROLE_DEPT_ADMIN"), List.of());
            Claims claims = jwtUtil.parseToken(token);
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) claims.get("roles", List.class);
            assertThat(roles).containsExactly("ROLE_ADMIN", "ROLE_DEPT_ADMIN");
        }

        @Test
        @DisplayName("应正确解析 permissions")
        void shouldParsePermissions() {
            String token = jwtUtil.generateToken("admin", List.of(), List.of("user:read", "user:write"));
            Claims claims = jwtUtil.parseToken(token);
            @SuppressWarnings("unchecked")
            List<String> perms = (List<String>) claims.get("permissions", List.class);
            assertThat(perms).containsExactly("user:read", "user:write");
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("空 roles 和 permissions 应正常生成")
        void shouldHandleEmptyLists() {
            String token = jwtUtil.generateToken("user", List.of(), List.of());
            assertThat(token).isNotNull();
            assertThat(jwtUtil.validateToken(token)).isTrue();
            assertThat(jwtUtil.getUsernameFromToken(token)).isEqualTo("user");
        }

        @Test
        @DisplayName("null token 不应通过 validate")
        void shouldRejectNullToken() {
            assertThat(jwtUtil.validateToken(null)).isFalse();
        }

        @Test
        @DisplayName("空字符串 token 不应通过 validate")
        void shouldRejectEmptyToken() {
            assertThat(jwtUtil.validateToken("")).isFalse();
        }

        @Test
        @DisplayName("格式错误的 token 不应通过 validate")
        void shouldRejectMalformedToken() {
            assertThat(jwtUtil.validateToken("not.a.jwt.token")).isFalse();
        }
    }
}
