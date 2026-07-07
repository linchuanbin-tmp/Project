package com.agent.user.service;

import com.agent.user.dto.NotificationActionRequest;
import com.agent.user.dto.NotificationResponse;
import com.agent.user.dto.NotificationSendRequest;
import java.util.List;

public interface SysNotificationService {

    List<NotificationResponse> listNotifications(Long userId, Integer status, String notifyType);

    int getUnreadCount(Long userId);

    void sendNotification(NotificationSendRequest request, Long senderId);

    void readNotification(Long id, Long userId);

    void handleAction(NotificationActionRequest request, Long userId);

    List<NotificationResponse> getThread(Long threadId, Long userId);
}
