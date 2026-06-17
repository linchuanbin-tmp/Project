package com.agent.user.service.impl;

import com.agent.user.dto.LoginRequest;
import com.agent.user.dto.LoginResponse;
import com.agent.user.dto.RegisterRequest;
import com.agent.user.dto.UserResponse;
import com.agent.user.entity.*;
import com.agent.user.mapper.*;
import com.agent.user.service.UserService;
import com.agent.user.utils.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Override
    public LoginResponse login(LoginRequest request) {
        // 1. 查询用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
        );

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (user.getStatus() != 1) {
            throw new RuntimeException("用户已被禁用");
        }

        // 2. 验证密码（BCrypt匹配）
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        // 3. 查询角色与权限
        List<SysRole> roles = sysRoleMapper.selectRolesByUserId(user.getId());
        List<SysPermission> permissions = sysPermissionMapper.selectPermissionsByUserId(user.getId());

        List<String> roleCodes = roles.stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toList());

        List<String> permCodes = permissions.stream()
                .map(SysPermission::getPermCode)
                .collect(Collectors.toList());

        // 4. 生成JWT
        String token = jwtUtil.generateToken(user.getUsername(), roleCodes, permCodes);

        return new LoginResponse(
                token,
                user.getUsername(),
                roleCodes,
                permCodes,
                user.getRealName()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest request) {
        // 1. 校验用户名是否重复
        User existingUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
        );
        if (existingUser != null) {
            throw new RuntimeException("用户名已存在");
        }

        // 2. 保存用户基本信息
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setRole("user"); // 兼容旧系统的普通字段描述
        user.setStatus(1); // 默认启用
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setDeleted(0);
        userMapper.insert(user);

        // 3. 关联默认普通员工角色 ROLE_USER
        SysRole userRole = sysRoleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getRoleCode, "ROLE_USER")
        );
        if (userRole != null) {
            SysUserRole mapping = new SysUserRole(user.getId(), userRole.getId());
            sysUserRoleMapper.insert(mapping);
        }
    }

    @Override
    public User getUserByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
        );
    }

    @Override
    public List<UserResponse> listUsers() {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().orderByDesc(User::getCreateTime));
        List<UserResponse> responses = new ArrayList<>();
        for (User user : users) {
            UserResponse response = new UserResponse();
            response.setId(user.getId());
            response.setUsername(user.getUsername());
            response.setRealName(user.getRealName());
            response.setStatus(user.getStatus());
            response.setCreateTime(user.getCreateTime());

            // 查询该用户的角色名称列表
            List<SysRole> roles = sysRoleMapper.selectRolesByUserId(user.getId());
            List<String> roleNames = roles.stream()
                    .map(SysRole::getRoleName)
                    .collect(Collectors.toList());
            response.setRoles(roleNames);

            responses.add(response);
        }
        return responses;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRole(Long userId, Long roleId) {
        // 1. 先清除现有的角色关系
        sysUserRoleMapper.delete(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
        );

        // 2. 写入新的关联
        SysUserRole mapping = new SysUserRole(userId, roleId);
        sysUserRoleMapper.insert(mapping);

        // 同步更新 sys_user 中的 role 字符串，兼容旧系统的单字段查询
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role != null) {
            User user = userMapper.selectById(userId);
            if (user != null) {
                // 如果是 ROLE_ADMIN 改为 admin，如果是 ROLE_USER 改为 user
                String code = role.getRoleCode();
                if ("ROLE_ADMIN".equalsIgnoreCase(code)) {
                    user.setRole("admin");
                } else {
                    user.setRole("user");
                }
                user.setUpdateTime(LocalDateTime.now());
                userMapper.updateById(user);
            }
        }
    }

    @Override
    public void updateStatus(Long userId, Integer status) {
        User user = userMapper.selectById(userId);
        if (user != null) {
            user.setStatus(status);
            user.setUpdateTime(LocalDateTime.now());
            userMapper.updateById(user);
        }
    }

    @Override
    public List<SysRole> listRoles() {
        return sysRoleMapper.selectList(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getStatus, 1)
        );
    }

    @Override
    public List<String> getRolesByUserId(Long userId) {
        List<SysRole> roles = sysRoleMapper.selectRolesByUserId(userId);
        return roles.stream().map(SysRole::getRoleCode).collect(Collectors.toList());
    }

    @Override
    public List<String> getPermissionsByUserId(Long userId) {
        List<SysPermission> permissions = sysPermissionMapper.selectPermissionsByUserId(userId);
        return permissions.stream().map(SysPermission::getPermCode).collect(Collectors.toList());
    }
}