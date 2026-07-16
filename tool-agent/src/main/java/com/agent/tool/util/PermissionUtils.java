package com.agent.tool.util;

import org.springframework.util.StringUtils;

/**
 * Unified permission-check utility class.
 * <p>
 * Role code conventions (from the DB sys_role table):
 * <ul>
 *   <li>{@code ROLE_ADMIN}      — System Administrator</li>
 *   <li>{@code ROLE_DEPT_ADMIN} — Department Administrator</li>
 *   <li>{@code ROLE_USER}       — Regular User</li>
 * </ul>
 * <p>
 * The roles parameter typically comes from the HTTP header {@code X-User-Roles},
 * forwarded by task-service, formatted as a comma-separated string,
 * e.g. {@code "ROLE_ADMIN,ROLE_USER"}.
 */
public final class PermissionUtils {

    private PermissionUtils() {
        // utility class
    }

    public static final String ROLE_ADMIN      = "ROLE_ADMIN";
    public static final String ROLE_DEPT_ADMIN = "ROLE_DEPT_ADMIN";
    public static final String ROLE_USER       = "ROLE_USER";

    /**
     * Whether the user is a system administrator.
     */
    public static boolean isAdmin(String rolesHeader) {
        return containsRole(rolesHeader, ROLE_ADMIN);
    }

    /**
     * Whether the user is a department administrator.
     */
    public static boolean isDeptAdmin(String rolesHeader) {
        return containsRole(rolesHeader, ROLE_DEPT_ADMIN);
    }

    /**
     * Whether the user is a department administrator or above (System Admin + Dept Admin).
     */
    public static boolean isDeptAdminOrAbove(String rolesHeader) {
        return containsRole(rolesHeader, ROLE_ADMIN) || containsRole(rolesHeader, ROLE_DEPT_ADMIN);
    }

    /**
     * Check whether the role header contains the specified role.
     * Compatible with both {@code ROLE_} prefixed and non-prefixed forms (fallback).
     *
     * @param rolesHeader comma-separated role string, e.g. {@code "ROLE_ADMIN,ROLE_USER"}
     * @param role        target role, e.g. {@code "ROLE_ADMIN"}
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
            // Compatible with non-ROLE_ prefixed forms
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
     * Assert that the current user is a system administrator, otherwise throw RuntimeException.
     * Used as an entry check for Controller methods requiring admin privileges.
     *
     * @throws RuntimeException if the user is not an administrator
     */
    public static void requireAdmin(String rolesHeader) {
        if (!isAdmin(rolesHeader)) {
            throw new RuntimeException("Unauthorized: Admin access required");
        }
    }
}
