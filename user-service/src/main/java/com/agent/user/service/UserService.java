package com.agent.user.service;

import com.agent.user.dto.*;
import com.agent.user.entity.User;
import com.agent.user.entity.SysRole;
import java.util.List;

public interface UserService {
    LoginResponse login(LoginRequest request);
    void register(RegisterRequest request);
    User getUserByUsername(String username);
    List<UserResponse> listUsers();
    void assignRole(Long userId, Long roleId);
    void updateStatus(Long userId, Integer status);
    List<SysRole> listRoles();
    List<String> getRolesByUserId(Long userId);
    List<String> getPermissionsByUserId(Long userId);
    void updateProfile(String username, UpdateProfileRequest request);
    void changePassword(String username, ChangePasswordRequest request);
}