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
    private final com.agent.user.mapper.SysDepartmentMapper sysDepartmentMapper;

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

    @PutMapping("/user/dept")
    public Result<String> updateUserDept(@Valid @RequestBody UpdateUserDeptRequest request) {
        try {
            userService.updateUserDept(request.getUserId(), request.getDeptId());
            return Result.success("Department updated successfully");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/user/clearance")
    public Result<String> updateUserClearance(@Valid @RequestBody UpdateUserClearanceRequest request) {
        try {
            userService.updateUserClearance(request.getUserId(), request.getClearanceLevel());
            return Result.success("Clearance level updated successfully");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/dept")
    public Result<String> createDept(@RequestBody com.agent.user.entity.SysDepartment dept) {
        try {
            sysDepartmentMapper.insert(dept);
            return Result.success("Department created successfully");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/dept")
    public Result<String> updateDept(@RequestBody com.agent.user.entity.SysDepartment dept) {
        try {
            sysDepartmentMapper.updateById(dept);
            return Result.success("Department updated successfully");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/dept/{id}")
    public Result<String> deleteDept(@PathVariable("id") Long id) {
        try {
            sysDepartmentMapper.deleteById(id);
            return Result.success("Department deleted successfully");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
