package com.agent.user.service.impl;

import com.agent.user.dto.*;
import com.agent.user.entity.*;
import com.agent.user.mapper.*;
import com.agent.user.service.EmailService;
import com.agent.user.service.UserService;
import com.agent.user.utils.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
    private final SysDepartmentMapper sysDepartmentMapper;
    private final StringRedisTemplate redisTemplate;
    private final SysConfigMapper sysConfigMapper;
    private final EmailService emailService;

    private static final String SESSION_KEY_PREFIX        = "session:active:";
    private static final String REDIS_KEY_SESSION_TIMEOUT = "sys:config:session_timeout";
    private static final String EMAIL_CODE_PREFIX          = "email:code:";
    private static final String EMAIL_CODE_LIMIT_PREFIX    = "email:code:limit:";
    private static final long   DEFAULT_SESSION_TIMEOUT   = 30L;
    private static final long   CODE_TTL_MINUTES           = 5L;
    private static final long   CODE_LIMIT_SECONDS         = 60L;

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

        // 2. 校验至少提供密码或验证码
        boolean hasCode = request.getCode() != null && !request.getCode().isEmpty();
        boolean hasPassword = request.getPassword() != null && !request.getPassword().isEmpty();
        if (!hasCode && !hasPassword) {
            throw new RuntimeException("请提供密码或验证码");
        }

        // 3. 验证身份：验证码模式或密码模式
        if (hasCode) {
            // 验证码登录模式
            String cachedCode = redisTemplate.opsForValue().get(EMAIL_CODE_PREFIX + request.getUsername());
            if (cachedCode == null || !cachedCode.equals(request.getCode())) {
                throw new RuntimeException("验证码错误或已过期");
            }
            // 验证成功后删除验证码
            redisTemplate.delete(EMAIL_CODE_PREFIX + request.getUsername());
        } else {
            // 密码登录模式
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new RuntimeException("密码错误");
            }
        }

        // 4. 查询角色与权限
        List<SysRole> roles = sysRoleMapper.selectRolesByUserId(user.getId());
        List<SysPermission> permissions = sysPermissionMapper.selectPermissionsByUserId(user.getId());

        List<String> roleCodes = roles.stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toList());

        List<String> permCodes = permissions.stream()
                .map(SysPermission::getPermCode)
                .collect(Collectors.toList());

        // 5. 生成JWT
        String token = jwtUtil.generateToken(user.getUsername(), roleCodes, permCodes);

        // 6. 写入 Redis session（滑动过期窗口）
        long timeoutMinutes = resolveSessionTimeout();
        redisTemplate.opsForValue().set(
                SESSION_KEY_PREFIX + user.getUsername(),
                token,
                timeoutMinutes,
                TimeUnit.MINUTES
        );

        return new LoginResponse(
                token,
                user.getUsername(),
                roleCodes,
                permCodes,
                user.getRealName()
        );
    }

    @Override
    public void logout(String username) {
        redisTemplate.delete(SESSION_KEY_PREFIX + username);
    }

    /**
     * Resolve session timeout: Redis cache → DB → default 30 min.
     */
    private long resolveSessionTimeout() {
        String cached = redisTemplate.opsForValue().get(REDIS_KEY_SESSION_TIMEOUT);
        if (cached != null) {
            try { return Long.parseLong(cached); } catch (NumberFormatException ignored) {}
        }
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getParamKey, "session_timeout")
        );
        long value = DEFAULT_SESSION_TIMEOUT;
        if (config != null) {
            try { value = Long.parseLong(config.getParamValue()); } catch (NumberFormatException ignored) {}
        }
        redisTemplate.opsForValue().set(REDIS_KEY_SESSION_TIMEOUT, String.valueOf(value));
        return value;
    }

    @Override
    public void sendVerificationCode(String email) {
        // 限流检查：60秒内不允许重复发送
        String limitKey = EMAIL_CODE_LIMIT_PREFIX + email;
        String existingLimit = redisTemplate.opsForValue().get(limitKey);
        if (existingLimit != null) {
            throw new RuntimeException("发送过于频繁，请60秒后再试");
        }

        // 生成6位随机验证码
        String code = String.format("%06d", (int)(Math.random() * 1000000));

        // 存入 Redis：有效期5分钟
        redisTemplate.opsForValue().set(
                EMAIL_CODE_PREFIX + email,
                code,
                CODE_TTL_MINUTES,
                TimeUnit.MINUTES
        );

        // 设置发送频率限制：60秒
        redisTemplate.opsForValue().set(
                limitKey,
                "1",
                CODE_LIMIT_SECONDS,
                TimeUnit.SECONDS
        );

        // 发送邮件
        emailService.sendVerificationCode(email, code);
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
            throw new RuntimeException("该邮箱已被注册");
        }

        // 2. 验证邮箱验证码
        String cachedCode = redisTemplate.opsForValue().get(EMAIL_CODE_PREFIX + request.getUsername());
        if (cachedCode == null || !cachedCode.equals(request.getCode())) {
            throw new RuntimeException("验证码错误或已过期");
        }
        // 验证成功后删除验证码
        redisTemplate.delete(EMAIL_CODE_PREFIX + request.getUsername());

        // 3. 保存用户基本信息
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

        // 4. 关联默认普通员工角色 ROLE_USER
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
            responses.add(convertToUserResponse(user));
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

    @Override
    public void updateProfile(String username, UpdateProfileRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        user.setRealName(request.getRealName());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public void changePassword(String username, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("New passwords do not match");
        }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    private UserResponse convertToUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setRealName(user.getRealName());
        response.setStatus(user.getStatus());
        response.setDeptId(user.getDeptId());
        response.setClearanceLevel(user.getClearanceLevel() != null ? user.getClearanceLevel() : 1);
        response.setCreateTime(user.getCreateTime());

        if (user.getDeptId() != null) {
            SysDepartment dept = sysDepartmentMapper.selectById(user.getDeptId());
            if (dept != null) {
                response.setDeptName(dept.getDeptName());
            }
        }

        List<SysRole> roles = sysRoleMapper.selectRolesByUserId(user.getId());
        List<String> roleNames = roles.stream()
                .map(SysRole::getRoleName)
                .collect(Collectors.toList());
        response.setRoles(roleNames);

        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserDept(Long userId, Long deptId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        if (deptId == null || deptId <= 0) {
            user.setDeptId(null);
        } else {
            SysDepartment dept = sysDepartmentMapper.selectById(deptId);
            if (dept == null) {
                throw new RuntimeException("Department not found");
            }
            user.setDeptId(deptId);
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserClearance(Long userId, Integer clearanceLevel) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        if (clearanceLevel == null || clearanceLevel < 1 || clearanceLevel > 3) {
            throw new RuntimeException("Invalid clearance level: " + clearanceLevel);
        }
        user.setClearanceLevel(clearanceLevel);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public List<UserResponse> listUsersByDept(Long deptId) {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getDeptId, deptId)
                        .orderByDesc(User::getCreateTime)
        );
        List<UserResponse> responses = new ArrayList<>();
        for (User user : users) {
            responses.add(convertToUserResponse(user));
        }
        return responses;
    }

    @Override
    public List<UserResponse> listUsersWithoutDept() {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .isNull(User::getDeptId)
                        .orderByDesc(User::getCreateTime)
        );
        List<UserResponse> responses = new ArrayList<>();
        for (User user : users) {
            responses.add(convertToUserResponse(user));
        }
        return responses;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addUsersToDept(List<Long> userIds, Long deptId) {
        if (deptId == null) {
            throw new RuntimeException("Department ID cannot be null");
        }
        SysDepartment dept = sysDepartmentMapper.selectById(deptId);
        if (dept == null) {
            throw new RuntimeException("Department not found");
        }
        for (Long userId : userIds) {
            User user = userMapper.selectById(userId);
            if (user != null) {
                user.setDeptId(deptId);
                user.setUpdateTime(LocalDateTime.now());
                userMapper.updateById(user);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeUserFromDept(Long userId, Long deptId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        if (user.getDeptId() != null && user.getDeptId().equals(deptId)) {
            user.setDeptId(null);
            user.setUpdateTime(LocalDateTime.now());
            userMapper.updateById(user);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetPassword(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        String tempPassword = UUID.randomUUID().toString().substring(0, 8) + "@1";
        user.setPassword(passwordEncoder.encode(tempPassword));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        return tempPassword;
    }
}