package com.agent.user.service;

import com.agent.user.dto.LoginRequest;
import com.agent.user.dto.LoginResponse;
import com.agent.user.entity.User;

public interface UserService {
    LoginResponse login(LoginRequest request);
    User getUserByUsername(String username);
}