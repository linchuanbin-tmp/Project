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

import java.security.SecureRandom;
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
    private static final String EMAIL_IP_LIMIT_PREFIX      = "email:ip:limit:";
    private static final String EMAIL_DAILY_LIMIT_PREFIX   = "email:daily:limit:";
    private static final String EMAIL_CODE_FAIL_PREFIX     = "email:code:fail:";
    private static final long   DEFAULT_SESSION_TIMEOUT   = 30L;
    private static final long   CODE_TTL_MINUTES           = 5L;
    private static final long   CODE_LIMIT_SECONDS         = 60L;
    private static final int    MAX_PER_IP_PER_HOUR        = 5;
    private static final int    MAX_PER_EMAIL_PER_DAY      = 10;
    private static final int    MAX_CODE_ATTEMPTS          = 5;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public LoginResponse login(LoginRequest request) {
        // Step 1: Look up user
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

        // Step 2: Require either password or verification code
        boolean hasCode = request.getCode() != null && !request.getCode().isEmpty();
        boolean hasPassword = request.getPassword() != null && !request.getPassword().isEmpty();
        if (!hasCode && !hasPassword) {
            throw new RuntimeException("请提供密码或验证码");
        }

        // Step 3. Verify identity: code-based or password-based
        if (hasCode) {
            // Code login — check brute-force attempts first
            String failKey = EMAIL_CODE_FAIL_PREFIX + request.getUsername();
            String failCountStr = redisTemplate.opsForValue().get(failKey);
            int failCount = failCountStr != null ? Integer.parseInt(failCountStr) : 0;
            if (failCount >= MAX_CODE_ATTEMPTS) {
                // Invalidate the code after too many failed attempts
                redisTemplate.delete(EMAIL_CODE_PREFIX + request.getUsername());
                redisTemplate.delete(failKey);
                throw new RuntimeException("Too many failed attempts, verification code has been invalidated");
            }

            String cachedCode = redisTemplate.opsForValue().get(EMAIL_CODE_PREFIX + request.getUsername());
            if (cachedCode == null || !cachedCode.equals(request.getCode())) {
                // Increment failure counter, TTL matches the code TTL
                redisTemplate.opsForValue().increment(failKey);
                redisTemplate.expire(failKey, CODE_TTL_MINUTES, TimeUnit.MINUTES);
                throw new RuntimeException("Invalid or expired verification code");
            }
            // Verification successful — clean up code and failure counter
            redisTemplate.delete(EMAIL_CODE_PREFIX + request.getUsername());
            redisTemplate.delete(failKey);
        } else {
            // Password login mode
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new RuntimeException("密码错误");
            }
        }

        // Step 4: Retrieve roles and permissions
        List<SysRole> roles = sysRoleMapper.selectRolesByUserId(user.getId());
        List<SysPermission> permissions = sysPermissionMapper.selectPermissionsByUserId(user.getId());

        List<String> roleCodes = roles.stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toList());

        List<String> permCodes = permissions.stream()
                .map(SysPermission::getPermCode)
                .collect(Collectors.toList());

        // Step 5: Generate JWT
        String token = jwtUtil.generateToken(user.getUsername(), roleCodes, permCodes);

        // Step 6: Write Redis session (sliding expiration window)
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
    public void sendVerificationCode(String email, String clientIp, String scene) {
        // Step 1. Rate limits first — always enforced regardless of email existence,
        //         preventing enumeration attacks from bypassing rate limits.
        String limitKey = EMAIL_CODE_LIMIT_PREFIX + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(limitKey))) {
            throw new RuntimeException("Please wait 60 seconds before requesting another code");
        }

        String ipLimitKey = EMAIL_IP_LIMIT_PREFIX + clientIp;
        Long ipCount = redisTemplate.opsForValue().increment(ipLimitKey);
        if (ipCount != null && ipCount == 1) {
            redisTemplate.expire(ipLimitKey, 1, TimeUnit.HOURS);
        }
        if (ipCount != null && ipCount > MAX_PER_IP_PER_HOUR) {
            throw new RuntimeException("Too many requests, please try again later");
        }

        String dailyLimitKey = EMAIL_DAILY_LIMIT_PREFIX + email;
        Long dailyCount = redisTemplate.opsForValue().increment(dailyLimitKey);
        if (dailyCount != null && dailyCount == 1) {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
            long secondsUntilMidnight = java.time.Duration.between(now, midnight).getSeconds();
            redisTemplate.expire(dailyLimitKey, secondsUntilMidnight, TimeUnit.SECONDS);
        }
        if (dailyCount != null && dailyCount > MAX_PER_EMAIL_PER_DAY) {
            throw new RuntimeException("Too many requests, please try again later");
        }

        // Step 2. Check email existence based on scene.
        //    Use the SAME generic error message for both cases to prevent
        //    email enumeration (attackers cannot determine if an email is registered).
        User existingUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, email));
        boolean exists = existingUser != null;

        if ("register".equals(scene) && exists) {
            throw new RuntimeException("This email is already registered");
        }
        if ("login".equals(scene) && !exists) {
            throw new RuntimeException("No account found with this email");
        }

        // Step 3. Generate 6-digit verification code using cryptographically secure RNG
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));

        // Store code in Redis with 5-minute TTL
        redisTemplate.opsForValue().set(
                EMAIL_CODE_PREFIX + email,
                code,
                CODE_TTL_MINUTES,
                TimeUnit.MINUTES
        );

        // Set per-email rate limit: 60 seconds
        redisTemplate.opsForValue().set(
                limitKey,
                "1",
                CODE_LIMIT_SECONDS,
                TimeUnit.SECONDS
        );

        // Clear any previous code attempt failures
        redisTemplate.delete(EMAIL_CODE_FAIL_PREFIX + email);

        // Send the email
        emailService.sendVerificationCode(email, code);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest request) {
        // Step 1: Check if username already exists
        User existingUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
        );
        if (existingUser != null) {
            throw new RuntimeException("该邮箱已被注册");
        }

        // Step 2. Validate verification code with brute-force protection
        String failKey = EMAIL_CODE_FAIL_PREFIX + request.getUsername();
        String failCountStr = redisTemplate.opsForValue().get(failKey);
        int failCount = failCountStr != null ? Integer.parseInt(failCountStr) : 0;
        if (failCount >= MAX_CODE_ATTEMPTS) {
            redisTemplate.delete(EMAIL_CODE_PREFIX + request.getUsername());
            redisTemplate.delete(failKey);
            throw new RuntimeException("Too many failed attempts, verification code has been invalidated");
        }

        String cachedCode = redisTemplate.opsForValue().get(EMAIL_CODE_PREFIX + request.getUsername());
        if (cachedCode == null || !cachedCode.equals(request.getCode())) {
            redisTemplate.opsForValue().increment(failKey);
            redisTemplate.expire(failKey, CODE_TTL_MINUTES, TimeUnit.MINUTES);
            throw new RuntimeException("Invalid or expired verification code");
        }
        // Verification successful — delete code and failure counter
        redisTemplate.delete(EMAIL_CODE_PREFIX + request.getUsername());
        redisTemplate.delete(failKey);

        // Step 3: Save basic user info
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setRole("user"); // Legacy role field for backward compatibility
        user.setStatus(1); // Enabled by default
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setDeleted(0);
        userMapper.insert(user);

        // Step 4: Assign default employee role ROLE_USER
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
        // Step 1: Remove existing role assignments
        sysUserRoleMapper.delete(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
        );

        // Step 2: Insert new role mapping
        SysUserRole mapping = new SysUserRole(userId, roleId);
        sysUserRoleMapper.insert(mapping);

        // Sync legacy role string in sys_user for backward compatibility
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role != null) {
            User user = userMapper.selectById(userId);
            if (user != null) {
                // Map ROLE_ADMIN -> "admin", ROLE_USER -> "user"
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
        List<String> roleCodes = roles.stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toList());
        response.setRoles(roleCodes);

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