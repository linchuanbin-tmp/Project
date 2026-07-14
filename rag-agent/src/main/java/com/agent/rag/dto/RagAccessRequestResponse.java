package com.agent.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagAccessRequestResponse {

    private Long documentId;

    private Long notificationId;

    private Long receiverId;

    private String status;

    private String message;
}
