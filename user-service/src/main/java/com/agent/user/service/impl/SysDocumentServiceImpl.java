package com.agent.user.service.impl;

import com.agent.user.dto.DocumentResponse;
import com.agent.user.entity.SysDocument;
import com.agent.user.entity.User;
import com.agent.user.entity.SysNotification;
import com.agent.user.mapper.SysDocumentMapper;
import com.agent.user.mapper.UserMapper;
import com.agent.user.mapper.SysNotificationMapper;
import com.agent.user.service.SysDocumentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SysDocumentServiceImpl implements SysDocumentService {

    @Autowired
    private SysDocumentMapper documentMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SysNotificationMapper notificationMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<DocumentResponse> listDocumentsForUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return new ArrayList<>();
        }

        Long deptId = user.getDeptId();
        Integer userClearance = user.getClearanceLevel() != null ? user.getClearanceLevel() : 1;

        // Fetch documents for the user's department or system documents
        LambdaQueryWrapper<SysDocument> query = new LambdaQueryWrapper<>();
        if (deptId != null) {
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

            // Check if user has sufficient clearance directly
            boolean hasClearance = userClearance >= doc.getSecurityLevel();
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
}
