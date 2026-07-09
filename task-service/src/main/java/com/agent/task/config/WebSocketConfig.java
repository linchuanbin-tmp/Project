package com.agent.task.config;

import com.agent.task.handler.TaskProgressWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TaskProgressWebSocketHandler taskProgressWebSocketHandler;

    public WebSocketConfig(TaskProgressWebSocketHandler taskProgressWebSocketHandler) {
        this.taskProgressWebSocketHandler = taskProgressWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(taskProgressWebSocketHandler, "/progress")
                .setAllowedOrigins("*");
    }
}
