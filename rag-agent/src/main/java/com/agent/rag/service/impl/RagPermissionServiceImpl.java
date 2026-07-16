package com.agent.rag.service.impl;

import com.agent.rag.dto.AccessibleDocumentDto;
import com.agent.rag.dto.RagPermissionSnapshot;
import com.agent.rag.entity.SysDocument;
import com.agent.rag.entity.SysNotification;
import com.agent.rag.entity.SysRole;
import com.agent.rag.entity.SysUser;
import com.agent.rag.mapper.SysDocumentMapper;
import com.agent.rag.mapper.SysNotificationMapper;
import com.agent.rag.mapper.SysRoleMapper;
import com.agent.rag.mapper.SysUserMapper;
import com.agent.rag.service.RagPermissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RagPermissionServiceImpl implements RagPermissionService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_DEPT_ADMIN = "ROLE_DEPT_ADMIN";
    private static final int RAG_APPROVAL_TTL_HOURS = 24;

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysDocumentMapper sysDocumentMapper;
    private final SysNotificationMapper sysNotificationMapper;
    private final ObjectMapper objectMapper;

    @Override
    public RagPermissionSnapshot resolveAccessibleDocuments(String username, String rolesHeader) {
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

        List<String> roles = resolveRoles(user.getId(), rolesHeader);
        boolean isAdmin = roles.contains(ROLE_ADMIN);

        Integer clearanceLevel = user.getClearanceLevel() != null ? user.getClearanceLevel() : 1;
        Set<Long> approvedDocumentIds = resolveApprovedDocumentIds(user.getId());

        List<SysDocument> documents = sysDocumentMapper.selectList(null);
        List<AccessibleDocumentDto> accessibleDocuments = new ArrayList<>();

        for (SysDocument document : documents) {
            String reason = resolveAccessReason(document, user, clearanceLevel, isAdmin, approvedDocumentIds);
            if (reason != null) {
                accessibleDocuments.add(AccessibleDocumentDto.builder()
                        .documentId(document.getId())
                        .title(document.getTitle())
                        .deptId(document.getDeptId())
                        .securityLevel(document.getSecurityLevel())
                        .accessReason(reason)
                        .build());
            }
        }

        List<Long> allowedDocumentIds = accessibleDocuments.stream()
                .map(AccessibleDocumentDto::getDocumentId)
                .collect(Collectors.toList());

        return RagPermissionSnapshot.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .deptId(user.getDeptId())
                .clearanceLevel(clearanceLevel)
                .roles(roles)
                .allowedDocumentIds(allowedDocumentIds)
                .accessibleDocuments(accessibleDocuments)
                .build();
    }

    private List<String> resolveRoles(Long userId, String rolesHeader) {
        Set<String> roles = new LinkedHashSet<>();
        if (StringUtils.hasText(rolesHeader)) {
            for (String role : rolesHeader.split(",")) {
                if (StringUtils.hasText(role)) {
                    roles.add(role.trim());
                }
            }
        }

        List<SysRole> dbRoles = sysRoleMapper.selectRolesByUserId(userId);
        for (SysRole role : dbRoles) {
            if (StringUtils.hasText(role.getRoleCode())) {
                roles.add(role.getRoleCode());
            }
        }
        return new ArrayList<>(roles);
    }

    private Set<Long> resolveApprovedDocumentIds(Long userId) {
        List<SysNotification> approvals = sysNotificationMapper.selectList(
                new LambdaQueryWrapper<SysNotification>()
                        .eq(SysNotification::getNotifyType, "RAG_APPLY")
                        .eq(SysNotification::getSenderId, userId)
                        .eq(SysNotification::getStatus, 3)
        );

        Set<Long> documentIds = new LinkedHashSet<>();
        for (SysNotification approval : approvals) {
            if (isApprovalExpired(approval)) {
                continue;
            }
            Long documentId = extractDocumentId(approval.getPayload());
            if (documentId != null) {
                documentIds.add(documentId);
            }
        }
        return documentIds;
    }

    private boolean isApprovalExpired(SysNotification approval) {
        LocalDateTime approvedAt = approval.getUpdateTime() != null ? approval.getUpdateTime() : approval.getCreateTime();
        if (approvedAt == null) {
            return true;
        }
        return approvedAt.plusHours(resolveAccessTtlHours(approval.getPayload())).isBefore(LocalDateTime.now());
    }

    private int resolveAccessTtlHours(String payload) {
        if (!StringUtils.hasText(payload)) {
            return RAG_APPROVAL_TTL_HOURS;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.has("accessTtlHours")) {
                int ttl = root.get("accessTtlHours").asInt(RAG_APPROVAL_TTL_HOURS);
                return ttl > 0 ? ttl : RAG_APPROVAL_TTL_HOURS;
            }
        } catch (Exception ignored) {
            // Malformed payloads fall back to the default temporary access window.
        }
        return RAG_APPROVAL_TTL_HOURS;
    }

    private Long extractDocumentId(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.has("documentId")) {
                return root.get("documentId").asLong();
            }
        } catch (Exception ignored) {
            // Invalid approval payloads are ignored instead of granting access.
        }
        return null;
    }

    private String resolveAccessReason(
            SysDocument document,
            SysUser user,
            Integer clearanceLevel,
            boolean isAdmin,
            Set<Long> approvedDocumentIds) {

        if (isAdmin) {
            return "ROLE_ADMIN";
        }

        if (approvedDocumentIds.contains(document.getId())) {
            return "RAG_APPLY_APPROVED";
        }

        boolean isGlobalDocument = document.getDeptId() == null;
        boolean sameDepartment = user.getDeptId() != null && user.getDeptId().equals(document.getDeptId());
        int requiredSecurity = document.getSecurityLevel() != null ? document.getSecurityLevel() : 1;
        boolean hasClearance = clearanceLevel >= requiredSecurity;

        if (isGlobalDocument && hasClearance) {
            return "GLOBAL_CLEARANCE";
        }
        if (sameDepartment && hasClearance) {
            return "DEPARTMENT_CLEARANCE";
        }

        return null;
    }
}
