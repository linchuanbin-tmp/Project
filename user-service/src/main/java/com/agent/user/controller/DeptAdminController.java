package com.agent.user.controller;

import com.agent.user.dto.*;
import com.agent.user.entity.User;
import com.agent.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/user/dept-admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DEPT_ADMIN') or hasRole('ADMIN')")
public class DeptAdminController {

    private final UserService userService;

    @GetMapping("/members")
    public Result<List<UserResponse>> listMembers(@RequestParam(value = "deptId", required = false) Long deptId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.getUserByUsername(username);
        if (user == null) {
            return Result.error(404, "User not found");
        }

        Long targetDeptId = deptId;
        List<String> roles = userService.getRolesByUserId(user.getId());
        boolean isAdmin = roles.contains("ROLE_ADMIN");

        if (!isAdmin || targetDeptId == null) {
            targetDeptId = user.getDeptId();
        }

        if (targetDeptId == null) {
            return Result.success(new ArrayList<>());
        }
        return Result.success(userService.listUsersByDept(targetDeptId));
    }

    @GetMapping("/candidates")
    public Result<List<UserResponse>> listCandidates() {
        return Result.success(userService.listUsersWithoutDept());
    }

    @PostMapping("/add-members")
    public Result<String> addMembers(@Valid @RequestBody AddMembersRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.getUserByUsername(username);
        if (user == null) {
            return Result.error(404, "User not found");
        }

        Long targetDeptId = request.getDeptId();
        List<String> roles = userService.getRolesByUserId(user.getId());
        boolean isAdmin = roles.contains("ROLE_ADMIN");

        if (!isAdmin || targetDeptId == null) {
            targetDeptId = user.getDeptId();
        }

        if (targetDeptId == null) {
            return Result.error(400, "Department ID is required");
        }
        try {
            userService.addUsersToDept(request.getUserIds(), targetDeptId);
            return Result.success("Members added successfully");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/remove-member")
    public Result<String> removeMember(@Valid @RequestBody RemoveMemberRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.getUserByUsername(username);
        if (user == null) {
            return Result.error(404, "User not found");
        }

        Long targetDeptId = request.getDeptId();
        List<String> roles = userService.getRolesByUserId(user.getId());
        boolean isAdmin = roles.contains("ROLE_ADMIN");

        if (!isAdmin || targetDeptId == null) {
            targetDeptId = user.getDeptId();
        }

        if (targetDeptId == null) {
            return Result.error(400, "Department ID is required");
        }
        try {
            userService.removeUserFromDept(request.getUserId(), targetDeptId);
            return Result.success("Member removed successfully");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
