package com.agent.user.controller;

import com.agent.user.dto.*;
import com.agent.user.entity.SysRole;
import com.agent.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;

    @GetMapping("/users")
    public Result<List<UserResponse>> listUsers() {
        return Result.success(userService.listUsers());
    }

    @PostMapping("/user/role")
    public Result<String> assignRole(@Valid @RequestBody AssignRoleRequest request) {
        try {
            userService.assignRole(request.getUserId(), request.getRoleId());
            return Result.success("Role assigned successfully");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/user/status")
    public Result<String> updateStatus(@Valid @RequestBody UpdateStatusRequest request) {
        try {
            userService.updateStatus(request.getUserId(), request.getStatus());
            return Result.success("Status updated successfully");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/roles")
    public Result<List<SysRole>> listRoles() {
        return Result.success(userService.listRoles());
    }
}
