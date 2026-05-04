package com.agent.tool.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
public class TaskProgressWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(10);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String taskId = getTaskId(session);
        sessions.put(taskId, session);
        log.info("WebSocket 连接建立: taskId={}", taskId);
        sendMessage(taskId, Map.of("progress", 0, "status", "connected", "message", "已连接，等待任务..."));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String taskId = getTaskId(session);
        String payload = message.getPayload();
        log.info("收到消息: taskId={}, payload={}", taskId, payload);

        // 启动模拟任务
        executor.execute(() -> runTask(taskId));
    }

    private void runTask(String taskId) {
        String[] steps = {
                "正在解析自然语言...",
                "正在查询数据库...",
                "正在调用 AI 模型推理...",
                "正在整理返回结果...",
                "任务完成！"
        };
        for (int i = 0; i <= 100; i += 20) {
            int stepIndex = Math.min(i / 20, steps.length - 1);
            sendMessage(taskId, Map.of(
                    "progress", i,
                    "status", i < 100 ? "running" : "completed",
                    "message", steps[stepIndex]
            ));
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String taskId = getTaskId(session);
        sessions.remove(taskId);
        log.info("WebSocket 连接关闭: taskId={}", taskId);
    }

    public void sendMessage(String taskId, Map<String, Object> data) {
        WebSocketSession session = sessions.get(taskId);
        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(data);
                session.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                log.error("发送失败", e);
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