package com.agent.user.service.impl;

import com.agent.user.dto.*;
import com.agent.user.entity.SysNotification;
import com.agent.user.entity.User;
import com.agent.user.mapper.SysNotificationMapper;
import com.agent.user.mapper.UserMapper;
import com.agent.user.service.SysNotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysNotificationServiceImpl implements SysNotificationService {

    private final SysNotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<NotificationResponse> listNotifications(Long userId, Integer status, String notifyType) {
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<>();
        
        if ("SENT".equalsIgnoreCase(notifyType)) {
            wrapper.eq(SysNotification::getSenderId, userId);
        } else {
            wrapper.eq(SysNotification::getReceiverId, userId);
            if (notifyType != null && !notifyType.isEmpty()) {
                wrapper.eq(SysNotification::getNotifyType, notifyType);
            }
        }

        if (status != null) {
            wrapper.eq(SysNotification::getStatus, status);
        }

        wrapper.orderByDesc(SysNotification::getCreateTime);
        List<SysNotification> list = notificationMapper.selectList(wrapper);

        // Fetch users to map IDs to names
        List<User> users = userMapper.selectList(null);
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        return list.stream().map(n -> {
            NotificationResponse resp = new NotificationResponse();
            resp.setId(n.getId());
            resp.setSenderId(n.getSenderId());
            resp.setReceiverId(n.getReceiverId());
            resp.setTitle(n.getTitle());
            resp.setContent(n.getContent());
            resp.setNotifyType(n.getNotifyType());
            resp.setStatus(n.getStatus());
            resp.setPayload(n.getPayload());
            resp.setParentId(n.getParentId());
            resp.setThreadId(n.getThreadId());
            resp.setCreateTime(n.getCreateTime());
            resp.setUpdateTime(n.getUpdateTime());

            if (n.getSenderId() == 0) {
                resp.setSenderName("system");
                resp.setSenderRealName("System");
            } else {
                User sender = userMap.get(n.getSenderId());
                if (sender != null) {
                    resp.setSenderName(sender.getUsername());
                    resp.setSenderRealName(sender.getRealName());
                } else {
                    resp.setSenderName("unknown");
                    resp.setSenderRealName("Unknown User");
                }
            }

            User receiver = userMap.get(n.getReceiverId());
            if (receiver != null) {
                resp.setReceiverName(receiver.getUsername());
                resp.setReceiverRealName(receiver.getRealName());
            } else {
                resp.setReceiverName("unknown");
                resp.setReceiverRealName("Unknown User");
            }

            return resp;
        }).collect(Collectors.toList());
    }

    @Override
    public int getUnreadCount(Long userId) {
        return notificationMapper.selectCount(
                new LambdaQueryWrapper<SysNotification>()
                        .eq(SysNotification::getReceiverId, userId)
                        .eq(SysNotification::getStatus, 0)
        ).intValue();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendNotification(NotificationSendRequest request, Long senderId) {
        User receiver = userMapper.selectById(request.getReceiverId());
        if (receiver == null) {
            throw new RuntimeException("Receiver user does not exist");
        }

        SysNotification notification = new SysNotification();
        notification.setSenderId(senderId);
        notification.setReceiverId(request.getReceiverId());
        notification.setTitle(request.getTitle());
        notification.setContent(request.getContent());
        notification.setNotifyType(request.getNotifyType() != null ? request.getNotifyType() : "CHAT");
        
        if ("RAG_APPLY".equalsIgnoreCase(request.getNotifyType()) || "SQL_AUDIT".equalsIgnoreCase(request.getNotifyType())) {
            notification.setStatus(2); // Pending Action
        } else {
            notification.setStatus(0); // Unread
        }
        
        notification.setPayload(request.getPayload());
        notification.setParentId(request.getParentId());
        
        if (request.getParentId() != null) {
            SysNotification parent = notificationMapper.selectById(request.getParentId());
            if (parent != null) {
                if (parent.getThreadId() != null) {
                    notification.setThreadId(parent.getThreadId());
                } else {
                    notification.setThreadId(parent.getId());
                    parent.setThreadId(parent.getId());
                    notificationMapper.updateById(parent);
                }
            } else {
                notification.setThreadId(null);
            }
        } else {
            notification.setThreadId(null);
        }
        
        notification.setCreateTime(LocalDateTime.now());
        notification.setUpdateTime(LocalDateTime.now());
        notification.setDeleted(0);

        notificationMapper.insert(notification);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void readNotification(Long id, Long userId) {
        SysNotification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new RuntimeException("Notification not found");
        }
        if (!notification.getReceiverId().equals(userId)) {
            throw new RuntimeException("Unauthorized operation");
        }
        if (notification.getStatus() == 0) {
            notification.setStatus(1); // Read
            notification.setUpdateTime(LocalDateTime.now());
            notificationMapper.updateById(notification);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleAction(NotificationActionRequest request, Long userId) {
        SysNotification notification = notificationMapper.selectById(request.getNotificationId());
        if (notification == null) {
            throw new RuntimeException("Notification not found");
        }
        if (!notification.getReceiverId().equals(userId)) {
            throw new RuntimeException("Unauthorized operation");
        }
        if (notification.getStatus() != 2) {
            throw new RuntimeException("Notification is not in pending approval state");
        }

        int targetStatus = "APPROVE".equalsIgnoreCase(request.getAction()) ? 3 : 4; // 3 = APPROVED, 4 = DENIED
        notification.setStatus(targetStatus);

        if (request.getOpinion() != null && !request.getOpinion().isEmpty()) {
            notification.setContent(notification.getContent() + " (Approval Opinion: " + request.getOpinion() + ")");
        }

        notification.setUpdateTime(LocalDateTime.now());
        notificationMapper.updateById(notification);

        // ── HITL Approval Callback: write Redis temp token after approval ──
        if (targetStatus == 3) { // APPROVED
            String notifyType = notification.getNotifyType();
            String payload = notification.getPayload();
            if (payload != null && !payload.isEmpty()) {
                try {
                    JsonNode node = objectMapper.readTree(payload);
                    if ("RAG_APPLY".equalsIgnoreCase(notifyType) && node.has("documentId")) {
                        // RAG document access approval: write temp access token, 24h
                        Long documentId = node.get("documentId").asLong();
                        Long senderId = notification.getSenderId();
                        String key = "rag:temp_access:" + senderId + ":" + documentId;
                        redisTemplate.opsForValue().set(key, "approved", 24, TimeUnit.HOURS);
                    } else if ("SQL_AUDIT".equalsIgnoreCase(notifyType) && node.has("sqlHash")) {
                        // SQL audit approval: write temp token, 5min
                        String sqlHash = node.get("sqlHash").asText();
                        Long senderId = notification.getSenderId();
                        String key = "sql:temp_approval:" + senderId + ":" + sqlHash;
                        redisTemplate.opsForValue().set(key, "approved", 5, TimeUnit.MINUTES);
                    }
                } catch (Exception e) {
                    // swallow parse errors — Redis token is best-effort; DB notification status is the source of truth
                }
            }
        }
    }

    @Override
    public List<NotificationResponse> getThread(Long threadId, Long userId) {
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotification::getThreadId, threadId)
               .orderByAsc(SysNotification::getCreateTime);
        List<SysNotification> list = notificationMapper.selectList(wrapper);

        boolean authorized = list.stream().anyMatch(n -> 
            n.getSenderId().equals(userId) || n.getReceiverId().equals(userId)
        );
        if (!authorized) {
            throw new RuntimeException("Unauthorized to view this message thread");
        }

        List<User> users = userMapper.selectList(null);
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        return list.stream().map(n -> {
            NotificationResponse resp = new NotificationResponse();
            resp.setId(n.getId());
            resp.setSenderId(n.getSenderId());
            resp.setReceiverId(n.getReceiverId());
            resp.setTitle(n.getTitle());
            resp.setContent(n.getContent());
            resp.setNotifyType(n.getNotifyType());
            resp.setStatus(n.getStatus());
            resp.setPayload(n.getPayload());
            resp.setParentId(n.getParentId());
            resp.setThreadId(n.getThreadId());
            resp.setCreateTime(n.getCreateTime());
            resp.setUpdateTime(n.getUpdateTime());

            if (n.getSenderId() == 0) {
                resp.setSenderName("system");
                resp.setSenderRealName("System");
            } else {
                User sender = userMap.get(n.getSenderId());
                if (sender != null) {
                    resp.setSenderName(sender.getUsername());
                    resp.setSenderRealName(sender.getRealName());
                } else {
                    resp.setSenderName("unknown");
                    resp.setSenderRealName("Unknown User");
                }
            }

            User receiver = userMap.get(n.getReceiverId());
            if (receiver != null) {
                resp.setReceiverName(receiver.getUsername());
                resp.setReceiverRealName(receiver.getRealName());
            } else {
                resp.setReceiverName("unknown");
                resp.setReceiverRealName("Unknown User");
            }

            return resp;
        }).collect(Collectors.toList());
    }
}
