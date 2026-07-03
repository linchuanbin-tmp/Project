package com.agent.user.controller;

import com.agent.user.dto.*;
import com.agent.user.entity.User;
import com.agent.user.service.SysNotificationService;
import com.agent.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final SysNotificationService notificationService;
    private final UserService userService;

    private Long getCurrentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if ("anonymousUser".equals(username)) {
            throw new RuntimeException("Not authenticated");
        }
        User user = userService.getUserByUsername(username);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        return user.getId();
    }

    @GetMapping("/list")
    public Result<List<NotificationResponse>> listNotifications(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String notifyType) {
        try {
            Long userId = getCurrentUserId();
            List<NotificationResponse> list = notificationService.listNotifications(userId, status, notifyType);
            return Result.success(list);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/unread-count")
    public Result<Integer> getUnreadCount() {
        try {
            Long userId = getCurrentUserId();
            int count = notificationService.getUnreadCount(userId);
            return Result.success(count);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/send")
    public Result<String> sendNotification(@Valid @RequestBody NotificationSendRequest request) {
        try {
            Long senderId = getCurrentUserId();
            notificationService.sendNotification(request, senderId);
            return Result.success("Message sent successfully");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/read/{id}")
    public Result<String> readNotification(@PathVariable Long id) {
        try {
            Long userId = getCurrentUserId();
            notificationService.readNotification(id, userId);
            return Result.success("Marked as read");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/action")
    public Result<String> handleAction(@Valid @RequestBody NotificationActionRequest request) {
        try {
            Long userId = getCurrentUserId();
            notificationService.handleAction(request, userId);
            return Result.success("Action completed successfully");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
