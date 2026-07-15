package com.agent.user.controller;

import com.agent.user.dto.*;
import com.agent.user.entity.User;
import com.agent.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final com.agent.user.mapper.SysDepartmentMapper sysDepartmentMapper;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = userService.login(request);
            return Result.success(response);
        } catch (RuntimeException e) {
            return Result.error(401, e.getMessage());
        }
    }

    @PostMapping("/logout")
    public Result<String> logout() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!"anonymousUser".equals(username)) {
            userService.logout(username);
        }
        return Result.success("Logged out successfully");
    }

    @PostMapping("/send-code")
    public Result<String> sendVerificationCode(@Valid @RequestBody SendCodeRequest request) {
        try {
            userService.sendVerificationCode(request.getEmail());
            return Result.success("Verification code sent");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/register")
    public Result<String> register(@Valid @RequestBody RegisterRequest request) {
        try {
            userService.register(request);
            return Result.success("Registration successful");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/info")
    public Result<UserInfoResponse> getInfo() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if ("anonymousUser".equals(username)) {
            return Result.error(401, "Not authenticated");
        }

        User user = userService.getUserByUsername(username);
        if (user == null) {
            return Result.error(404, "User not found");
        }

        List<String> roles = userService.getRolesByUserId(user.getId());
        List<String> permissions = userService.getPermissionsByUserId(user.getId());

        String deptName = null;
        if (user.getDeptId() != null) {
            com.agent.user.entity.SysDepartment dept = sysDepartmentMapper.selectById(user.getDeptId());
            if (dept != null) {
                deptName = dept.getDeptName();
            }
        }

        UserInfoResponse response = new UserInfoResponse(
                user.getUsername(),
                user.getRealName(),
                roles,
                permissions,
                user.getDeptId(),
                deptName,
                user.getClearanceLevel() != null ? user.getClearanceLevel() : 1
        );
        return Result.success(response);
    }

    @PutMapping("/profile")
    public Result<String> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if ("anonymousUser".equals(username)) {
            return Result.error(401, "Not authenticated");
        }
        try {
            userService.updateProfile(username, request);
            return Result.success("Profile updated successfully");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/password")
    public Result<String> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if ("anonymousUser".equals(username)) {
            return Result.error(401, "Not authenticated");
        }
        try {
            userService.changePassword(username, request);
            return Result.success("Password changed successfully");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/list")
    public Result<List<UserResponse>> listUsers() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if ("anonymousUser".equals(username)) {
            return Result.error(401, "Not authenticated");
        }
        User currentUser = userService.getUserByUsername(username);
        if (currentUser == null) {
            return Result.error(404, "User not found");
        }

        List<String> roles = userService.getRolesByUserId(currentUser.getId());
        boolean isAdmin = roles.contains("ROLE_ADMIN");

        if (isAdmin) {
            return Result.success(userService.listUsers());
        }

        // 非管理员只返回同部门用户
        if (currentUser.getDeptId() != null) {
            return Result.success(userService.listUsersByDept(currentUser.getDeptId()));
        }
        return Result.success(List.of());
    }

    @GetMapping("/dept/list")
    public Result<List<com.agent.user.entity.SysDepartment>> listDepartments() {
        return Result.success(sysDepartmentMapper.selectList(null));
    }
}