package com.agent.user.service.impl;

import com.agent.user.dto.DocumentResponse;
import com.agent.user.entity.SysDocument;
import com.agent.user.entity.User;
import com.agent.user.entity.SysNotification;
import com.agent.user.mapper.SysDocumentMapper;
import com.agent.user.mapper.UserMapper;
import com.agent.user.mapper.SysNotificationMapper;
import com.agent.user.service.RagIndexSyncClient;
import com.agent.user.service.SysDocumentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SysDocumentServiceImpl implements SysDocumentService {

    @Autowired
    private com.agent.user.service.UserService userService;

    @Autowired
    private SysDocumentMapper documentMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SysNotificationMapper notificationMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RagIndexSyncClient ragIndexSyncClient;

    @Override
    public List<DocumentResponse> listDocumentsForUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return new ArrayList<>();
        }

        Long deptId = user.getDeptId();
        Integer userClearance = user.getClearanceLevel() != null ? user.getClearanceLevel() : 1;
        List<String> roles = userService.getRolesByUserId(userId);
        boolean isAdmin = roles.contains("ROLE_ADMIN");

        // Fetch documents for the user's department or system documents
        LambdaQueryWrapper<SysDocument> query = new LambdaQueryWrapper<>();
        if (isAdmin) {
            // Super Admin can list all documents (both global and department documents)
            // No restriction on the query wrapper
        } else if (deptId != null) {
            query.eq(SysDocument::getDeptId, deptId).or().isNull(SysDocument::getDeptId);
        } else {
            query.isNull(SysDocument::getDeptId);
        }

        List<SysDocument> docs = documentMapper.selectList(query);

        List<DocumentResponse> list = new ArrayList<>();
        for (SysDocument doc : docs) {
            DocumentResponse resp = new DocumentResponse();
            resp.setId(doc.getId());
            resp.setTitle(doc.getTitle());
            resp.setDeptId(doc.getDeptId());
            resp.setSecurityLevel(doc.getSecurityLevel());
            resp.setCreateTime(doc.getCreateTime());

            // Check if user belongs to the department OR if it is a system/global document
            boolean belongsToDept = doc.getDeptId() == null || (user.getDeptId() != null && user.getDeptId().equals(doc.getDeptId()));
            boolean hasClearance = isAdmin || (belongsToDept && (userClearance >= doc.getSecurityLevel()));
            boolean isApproved = false;

            if (!hasClearance) {
                // Check if user has an approved RAG_APPLY notification for this document
                List<SysNotification> approvedNotifications = notificationMapper.selectList(
                    new LambdaQueryWrapper<SysNotification>()
                        .eq(SysNotification::getNotifyType, "RAG_APPLY")
                        .eq(SysNotification::getSenderId, userId)
                        .eq(SysNotification::getStatus, 3) // 3 = Approved
                );
                for (SysNotification notif : approvedNotifications) {
                    String payload = notif.getPayload();
                    if (payload != null && !payload.isEmpty()) {
                        try {
                            JsonNode node = objectMapper.readTree(payload);
                            if (node.has("documentId") && node.get("documentId").asLong() == doc.getId().longValue()) {
                                isApproved = true;
                                break;
                            }
                        } catch (Exception e) {
                            // ignore parse errors
                        }
                    }
                }
            }

            if (hasClearance || isApproved) {
                resp.setAccessible(true);
                resp.setContent(doc.getContent());
            } else {
                resp.setAccessible(false);
                resp.setContent("Access Restricted. Required Clearance: Level " + doc.getSecurityLevel() + ". Please submit an access request.");
            }

            list.add(resp);
        }

        return list;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void createDocument(SysDocument document, Long creatorId) {
        User creator = userMapper.selectById(creatorId);
        if (creator == null) {
            throw new RuntimeException("Creator user not found");
        }
        
        List<String> roles = userService.getRolesByUserId(creatorId);
        boolean isAdmin = roles.contains("ROLE_ADMIN");
        boolean isDeptAdmin = roles.contains("ROLE_DEPT_ADMIN");
        
        if (isAdmin) {
            // Super Admin can set deptId to null (system doc) or custom deptId
            // We do not overwrite it here, allowing them to create system docs or select a dept
        } else if (isDeptAdmin) {
            if (creator.getDeptId() == null) {
                throw new RuntimeException("Department Administrator must belong to a department");
            }
            document.setDeptId(creator.getDeptId()); // Dept Admin can only create documents for their own department
        } else {
            throw new RuntimeException("Unauthorized operation");
        }
        
        document.setCreateTime(LocalDateTime.now());
        documentMapper.insert(document);
        ragIndexSyncClient.indexDocumentAfterCommit(document.getId());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void updateDocument(SysDocument document, Long updaterId) {
        SysDocument existing = documentMapper.selectById(document.getId());
        if (existing == null) {
            throw new RuntimeException("Document not found");
        }
        
        User updater = userMapper.selectById(updaterId);
        if (updater == null) {
            throw new RuntimeException("Updater user not found");
        }
        
        List<String> roles = userService.getRolesByUserId(updaterId);
        boolean isAdmin = roles.contains("ROLE_ADMIN");
        boolean isDeptAdmin = roles.contains("ROLE_DEPT_ADMIN");
        
        if (isAdmin) {
            // Super Admin can edit any document they can access
            // Let them edit global docs and any dept docs if they want, but let's keep metadata flexible
        } else if (isDeptAdmin) {
            if (updater.getDeptId() == null || !updater.getDeptId().equals(existing.getDeptId())) {
                throw new RuntimeException("Department Administrator can only modify their own department documents");
            }
            document.setDeptId(updater.getDeptId());
        } else {
            throw new RuntimeException("Unauthorized operation");
        }
        
        existing.setTitle(document.getTitle());
        existing.setContent(document.getContent());
        existing.setSecurityLevel(document.getSecurityLevel());
        existing.setDeptId(document.getDeptId());
        
        documentMapper.updateById(existing);
        ragIndexSyncClient.indexDocumentAfterCommit(existing.getId());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long id, Long deleterId) {
        SysDocument existing = documentMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("Document not found");
        }
        
        User deleter = userMapper.selectById(deleterId);
        if (deleter == null) {
            throw new RuntimeException("Deleter user not found");
        }
        
        List<String> roles = userService.getRolesByUserId(deleterId);
        boolean isAdmin = roles.contains("ROLE_ADMIN");
        boolean isDeptAdmin = roles.contains("ROLE_DEPT_ADMIN");
        
        if (isAdmin) {
            // Super Admin can delete global documents
            if (existing.getDeptId() != null) {
                // If it belongs to a dept, let's make sure they can delete it only if they want
            }
        } else if (isDeptAdmin) {
            if (deleter.getDeptId() == null || !deleter.getDeptId().equals(existing.getDeptId())) {
                throw new RuntimeException("Department Administrator can only delete their own department documents");
            }
        } else {
            throw new RuntimeException("Unauthorized operation");
        }
        
        documentMapper.deleteById(id);
        ragIndexSyncClient.deleteDocumentIndexAfterCommit(id);
    }
}
