package com.agent.tool.util;

import org.springframework.util.StringUtils;

/**
 * 统一权限判断工具类。
 * <p>
 * 角色编码约定（来自 DB sys_role 表）：
 * <ul>
 *   <li>{@code ROLE_ADMIN}      — 系统管理员</li>
 *   <li>{@code ROLE_DEPT_ADMIN} — 部门管理员</li>
 *   <li>{@code ROLE_USER}       — 普通用户</li>
 * </ul>
 * <p>
 * roles 参数通常来自 HTTP Header {@code X-User-Roles}，由 task-service 转发时传入，
 * 格式为逗号分隔的字符串，例如 {@code "ROLE_ADMIN,ROLE_USER"}。
 */
public final class PermissionUtils {

    private PermissionUtils() {
        // utility class
    }

    public static final String ROLE_ADMIN      = "ROLE_ADMIN";
    public static final String ROLE_DEPT_ADMIN = "ROLE_DEPT_ADMIN";
    public static final String ROLE_USER       = "ROLE_USER";

    /**
     * 是否为系统管理员。
     */
    public static boolean isAdmin(String rolesHeader) {
        return containsRole(rolesHeader, ROLE_ADMIN);
    }

    /**
     * 是否为部门管理员。
     */
    public static boolean isDeptAdmin(String rolesHeader) {
        return containsRole(rolesHeader, ROLE_DEPT_ADMIN);
    }

    /**
     * 是否为部门管理员或以上（系统管理员 + 部门管理员）。
     */
    public static boolean isDeptAdminOrAbove(String rolesHeader) {
        return containsRole(rolesHeader, ROLE_ADMIN) || containsRole(rolesHeader, ROLE_DEPT_ADMIN);
    }

    /**
     * 检查角色头中是否包含指定角色。
     * 同时兼容带 {@code ROLE_} 前缀和不带前缀的写法（兜底）。
     *
     * @param rolesHeader 逗号分隔的角色字符串，如 {@code "ROLE_ADMIN,ROLE_USER"}
     * @param role        目标角色，如 {@code "ROLE_ADMIN"}
     */
    private static boolean containsRole(String rolesHeader, String role) {
        if (!StringUtils.hasText(rolesHeader)) {
            return false;
        }
        for (String r : rolesHeader.split(",")) {
            String trimmed = r.trim();
            if (role.equalsIgnoreCase(trimmed)) {
                return true;
            }
            // 兼容不带 ROLE_ 前缀的写法
            if (role.startsWith("ROLE_")) {
                String shortRole = role.substring(5);
                if (shortRole.equalsIgnoreCase(trimmed)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 断言当前用户是系统管理员，否则抛出 RuntimeException。
     * 用于 Controller 中需要管理员权限的方法入口检查。
     *
     * @throws RuntimeException 如果不是管理员
     */
    public static void requireAdmin(String rolesHeader) {
        if (!isAdmin(rolesHeader)) {
            throw new RuntimeException("Unauthorized: Admin access required");
        }
    }
}
