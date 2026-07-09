package com.agent.task.handler;

import com.agent.task.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class TaskProgressWebSocketHandler extends TextWebSocketHandler {

    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    @Lazy
    private TaskService taskService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String taskId = getTaskId(session);
        sessions.put(taskId, session);
        log.info("Task WS connection established: taskId={}", taskId);
        
        sendMessage(taskId, Map.of(
            "progress", 0,
            "status", "connected",
            "message", "Connection established, initializing..."
        ));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String taskId = getTaskId(session);
        String payload = message.getPayload();
        log.info("Received WS command: taskId={}, payload={}", taskId, payload);

        try {
            Map<?, ?> command = objectMapper.readValue(payload, Map.class);
            String taskType = (String) command.get("taskType");
            String query = (String) command.get("query");

            // Execute using the service
            taskService.submitTaskFromWebSocket(taskId, taskType, query);
        } catch (Exception e) {
            log.error("Failed to parse WS command", e);
            sendMessage(taskId, Map.of(
                "progress", 0,
                "status", "error",
                "message", "Invalid command format: " + e.getMessage()
            ));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String taskId = getTaskId(session);
        sessions.remove(taskId);
        log.info("Task WS connection closed: taskId={}", taskId);
    }

    public static void sendMessage(String taskId, Map<String, Object> data) {
        WebSocketSession session = sessions.get(taskId);
        if (session != null && session.isOpen()) {
            try {
                String json = new ObjectMapper().writeValueAsString(data);
                session.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                log.error("Failed to send WS message for taskId={}", taskId, e);
            }
        }
    }

    private String getTaskId(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query != null && query.contains("taskId=")) {
            return query.split("taskId=")[1].split("&")[0];
        }
        return session.getId();
    }
}
