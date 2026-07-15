package com.agent.tool.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("权限判断工具类")
class PermissionUtilsTest {

    @Nested
    @DisplayName("isAdmin")
    class IsAdmin {

        @Test
        @DisplayName("包含 ROLE_ADMIN 应返回 true")
        void shouldReturnTrueForAdmin() {
            assertThat(PermissionUtils.isAdmin("ROLE_ADMIN,ROLE_USER")).isTrue();
        }

        @Test
        @DisplayName("仅 ROLE_ADMIN 应返回 true")
        void shouldReturnTrueForAdminAlone() {
            assertThat(PermissionUtils.isAdmin("ROLE_ADMIN")).isTrue();
        }

        @Test
        @DisplayName("仅有 ROLE_USER 应返回 false")
        void shouldReturnFalseForUser() {
            assertThat(PermissionUtils.isAdmin("ROLE_USER")).isFalse();
        }

        @Test
        @DisplayName("仅有 ROLE_DEPT_ADMIN 应返回 false")
        void shouldReturnFalseForDeptAdmin() {
            assertThat(PermissionUtils.isAdmin("ROLE_DEPT_ADMIN")).isFalse();
        }

        @Test
        @DisplayName("空字符串应返回 false")
        void shouldReturnFalseForEmpty() {
            assertThat(PermissionUtils.isAdmin("")).isFalse();
        }

        @Test
        @DisplayName("null 应返回 false")
        void shouldReturnFalseForNull() {
            assertThat(PermissionUtils.isAdmin(null)).isFalse();
        }

        @Test
        @DisplayName("兼容不带 ROLE_ 前缀的 ADMIN")
        void shouldAcceptShortFormAdmin() {
            assertThat(PermissionUtils.isAdmin("ADMIN")).isTrue();
        }
    }

    @Nested
    @DisplayName("isDeptAdmin")
    class IsDeptAdmin {

        @Test
        @DisplayName("包含 ROLE_DEPT_ADMIN 应返回 true")
        void shouldReturnTrueForDeptAdmin() {
            assertThat(PermissionUtils.isDeptAdmin("ROLE_DEPT_ADMIN,ROLE_USER")).isTrue();
        }

        @Test
        @DisplayName("仅有 ROLE_ADMIN 应返回 false")
        void shouldReturnFalseForAdmin() {
            assertThat(PermissionUtils.isDeptAdmin("ROLE_ADMIN")).isFalse();
        }

        @Test
        @DisplayName("兼容不带 ROLE_ 前缀的 DEPT_ADMIN")
        void shouldAcceptShortFormDeptAdmin() {
            assertThat(PermissionUtils.isDeptAdmin("DEPT_ADMIN")).isTrue();
        }
    }

    @Nested
    @DisplayName("isDeptAdminOrAbove")
    class IsDeptAdminOrAbove {

        @Test
        @DisplayName("系统管理员应为 true")
        void shouldReturnTrueForAdmin() {
            assertThat(PermissionUtils.isDeptAdminOrAbove("ROLE_ADMIN")).isTrue();
        }

        @Test
        @DisplayName("部门管理员应为 true")
        void shouldReturnTrueForDeptAdmin() {
            assertThat(PermissionUtils.isDeptAdminOrAbove("ROLE_DEPT_ADMIN")).isTrue();
        }

        @Test
        @DisplayName("普通用户应为 false")
        void shouldReturnFalseForUser() {
            assertThat(PermissionUtils.isDeptAdminOrAbove("ROLE_USER")).isFalse();
        }
    }

    @Nested
    @DisplayName("requireAdmin")
    class RequireAdmin {

        @Test
        @DisplayName("管理员不抛异常")
        void shouldNotThrowForAdmin() {
            PermissionUtils.requireAdmin("ROLE_ADMIN,ROLE_USER");
        }

        @Test
        @DisplayName("非管理员应抛 RuntimeException")
        void shouldThrowForNonAdmin() {
            assertThatThrownBy(() -> PermissionUtils.requireAdmin("ROLE_USER"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Unauthorized");
        }

        @Test
        @DisplayName("空角色应抛 RuntimeException")
        void shouldThrowForEmpty() {
            assertThatThrownBy(() -> PermissionUtils.requireAdmin(""))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
