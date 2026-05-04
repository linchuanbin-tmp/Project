package com.agent.user.controller;

import com.agent.user.dto.LoginRequest;
import com.agent.user.dto.LoginResponse;
import com.agent.user.dto.Result;
import com.agent.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = userService.login(request);
            return Result.success(response);
        } catch (RuntimeException e) {
            return Result.error(401, e.getMessage());
        }
    }

    @GetMapping("/info")
    public Result<String> getInfo() {
        // 后续从JWT解析用户信息
        return Result.success("用户信息");
    }
}