package com.agent.rag.service.impl;

import com.agent.rag.dto.RagAccessRequest;
import com.agent.rag.dto.RagAccessRequestResponse;
import com.agent.rag.dto.RagPermissionSnapshot;
import com.agent.rag.entity.SysDocument;
import com.agent.rag.entity.SysNotification;
import com.agent.rag.entity.SysRole;
import com.agent.rag.entity.SysUser;
import com.agent.rag.mapper.SysDocumentMapper;
import com.agent.rag.mapper.SysNotificationMapper;
import com.agent.rag.mapper.SysRoleMapper;
import com.agent.rag.mapper.SysUserMapper;
import com.agent.rag.service.RagAccessRequestService;
import com.agent.rag.service.RagPermissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RagAccessRequestServiceImpl implements RagAccessRequestService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_DEPT_ADMIN = "ROLE_DEPT_ADMIN";
    private static final String NOTIFY_TYPE_RAG_APPLY = "RAG_APPLY";
    private static final int STATUS_PENDING = 2;

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysDocumentMapper sysDocumentMapper;
    private final SysNotificationMapper sysNotificationMapper;
    private final RagPermissionService ragPermissionService;
    private final ObjectMapper objectMapper;

    @Override
    public RagAccessRequestResponse requestAccess(RagAccessRequest request, String username, String rolesHeader) {
        SysUser requester = resolveRequester(username);
        SysDocument document = sysDocumentMapper.selectById(request.getDocumentId());
        if (document == null) {
            throw new RuntimeException("Document not found: " + request.getDocumentId());
        }

        RagPermissionSnapshot permission = ragPermissionService.resolveAccessibleDocuments(username, rolesHeader);
        if (permission.getAllowedDocumentIds() != null && permission.getAllowedDocumentIds().contains(document.getId())) {
            return RagAccessRequestResponse.builder()
                    .documentId(document.getId())
                    .status("ALREADY_ACCESSIBLE")
                    .message("Current user can already access this document.")
                    .build();
        }

        SysNotification existing = findPendingRequest(requester.getId(), document.getId());
        if (existing != null) {
            return RagAccessRequestResponse.builder()
                    .documentId(document.getId())
                    .notificationId(existing.getId())
                    .receiverId(existing.getReceiverId())
                    .status("PENDING")
                    .message("A pending RAG access request already exists.")
                    .build();
        }

        SysUser reviewer = resolveReviewer(document);
        SysNotification notification = new SysNotification();
        notification.setSenderId(requester.getId());
        notification.setReceiverId(reviewer.getId());
        notification.setTitle("RAG Permission Escalation Request");
        notification.setContent("Employee @" + requester.getUsername() + " requests temporary access to \"" + document.getTitle()
                + "\" (Security: Level-" + safeSecurityLevel(document) + ").");
        notification.setNotifyType(NOTIFY_TYPE_RAG_APPLY);
        notification.setStatus(STATUS_PENDING);
        notification.setPayload(buildPayload(document, requester, request.getReason()));
        notification.setDeleted(0);
        notification.setCreateTime(LocalDateTime.now());
        notification.setUpdateTime(LocalDateTime.now());
        sysNotificationMapper.insert(notification);

        return RagAccessRequestResponse.builder()
                .documentId(document.getId())
                .notificationId(notification.getId())
                .receiverId(reviewer.getId())
                .status("PENDING")
                .message("RAG access request submitted for approval.")
                .build();
    }

    private SysUser resolveRequester(String username) {
        if (!StringUtils.hasText(username) || "anonymousUser".equals(username)) {
            throw new RuntimeException("Not authenticated");
        }
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .eq(SysUser::getStatus, 1)
        );
        if (user == null) {
            throw new RuntimeException("User not found or disabled: " + username);
        }
        return user;
    }

    private SysNotification findPendingRequest(Long requesterId, Long documentId) {
        List<SysNotification> pending = sysNotificationMapper.selectList(
                new LambdaQueryWrapper<SysNotification>()
                        .eq(SysNotification::getNotifyType, NOTIFY_TYPE_RAG_APPLY)
                        .eq(SysNotification::getSenderId, requesterId)
                        .eq(SysNotification::getStatus, STATUS_PENDING)
                        .orderByDesc(SysNotification::getCreateTime)
        );
        for (SysNotification notification : pending) {
            Long payloadDocumentId = extractDocumentId(notification.getPayload());
            if (documentId.equals(payloadDocumentId)) {
                return notification;
            }
        }
        return null;
    }

    private SysUser resolveReviewer(SysDocument document) {
        if (document.getDeptId() != null) {
            SysUser deptAdmin = findActiveUserByRole(document.getDeptId(), ROLE_DEPT_ADMIN);
            if (deptAdmin != null) {
                return deptAdmin;
            }
        }
        SysUser admin = findActiveUserByRole(null, ROLE_ADMIN);
        if (admin == null) {
            throw new RuntimeException("No reviewer found for RAG access request.");
        }
        return admin;
    }

    private SysUser findActiveUserByRole(Long deptId, String roleCode) {
        List<SysUser> users = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStatus, 1)
                        .orderByAsc(SysUser::getId)
        );
        for (SysUser user : users) {
            if (deptId != null && !deptId.equals(user.getDeptId())) {
                continue;
            }
            List<SysRole> roles = sysRoleMapper.selectRolesByUserId(user.getId());
            boolean matched = roles.stream().anyMatch(role -> roleCode.equals(role.getRoleCode()));
            if (matched) {
                return user;
            }
        }
        return null;
    }

    private String buildPayload(SysDocument document, SysUser requester, String reason) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("documentId", document.getId());
            payload.put("title", document.getTitle());
            payload.put("deptId", document.getDeptId());
            payload.put("clearanceLevel", safeSecurityLevel(document));
            payload.put("requesterId", requester.getId());
            payload.put("requesterUsername", requester.getUsername());
            payload.put("reason", StringUtils.hasText(reason) ? reason : "Required for business knowledge retrieval.");
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build RAG access request payload: " + e.getMessage());
        }
    }

    private Long extractDocumentId(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        try {
            if (objectMapper.readTree(payload).has("documentId")) {
                return objectMapper.readTree(payload).get("documentId").asLong();
            }
        } catch (Exception ignored) {
            // Ignore malformed pending request payloads.
        }
        return null;
    }

    private int safeSecurityLevel(SysDocument document) {
        return document.getSecurityLevel() != null ? document.getSecurityLevel() : 1;
    }
}
