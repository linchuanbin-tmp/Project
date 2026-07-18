package com.agent.task.service.impl;

import com.agent.task.entity.TaskRecord;
import com.agent.task.handler.TaskProgressWebSocketHandler;
import com.agent.task.mapper.TaskRecordMapper;
import com.agent.task.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TaskServiceImpl implements TaskService {

    private static final int CORE_POOL_SIZE = 5;
    private static final int MAX_POOL_SIZE  = 10;
    private static final int QUEUE_CAPACITY = 100;
    private static final int MAX_RETRY      = 3;

    private final TaskRecordMapper taskRecordMapper;
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = new ThreadPoolExecutor(
            CORE_POOL_SIZE, MAX_POOL_SIZE, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(QUEUE_CAPACITY),
            new ThreadPoolExecutor.AbortPolicy()
    );

    private final String codeAgentUrl;
    private final String toolAgentUrl;
    private final String ragAgentUrl;
    private final String userServiceUrl;

    public TaskServiceImpl(TaskRecordMapper taskRecordMapper, JdbcTemplate jdbcTemplate) {
        this.taskRecordMapper = taskRecordMapper;
        this.jdbcTemplate = jdbcTemplate;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(120000);
        this.restTemplate = new RestTemplate(factory);
        
        String codeUri = System.getenv("CODE_SERVICE_URI");
        this.codeAgentUrl = (codeUri != null ? codeUri : "http://localhost:8084") + "/code/query";
        
        String toolUri = System.getenv("TOOL_SERVICE_URI");
        this.toolAgentUrl = (toolUri != null ? toolUri : "http://localhost:8083") + "/tool/execute";

        String ragUri = System.getenv("RAG_SERVICE_URI");
        this.ragAgentUrl = (ragUri != null ? ragUri : "http://localhost:8085") + "/rag/query";

        String userUri = System.getenv("USER_SERVICE_URI");
        this.userServiceUrl = (userUri != null ? userUri : "http://localhost:8081") + "/user";
    }

    @Override
    public TaskRecord submitTask(String username, String rolesHeader, String taskType, String input) {
        Long userId = getUserIdByUsername(username);

        // Step 1: Create database record
        TaskRecord record = new TaskRecord();
        record.setTaskType(taskType);
        record.setStatus("INIT");
        record.setUserId(userId);
        record.setInput(input);
        record.setAttemptCount(0);
        record.setElapsedTime(0);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        taskRecordMapper.insert(record);

        log.info("Task created in database: taskId={}, type={}", record.getId(), taskType);

        // Step 2: Execute task asynchronously (return friendly error if queue full)
        String wsTaskId = String.valueOf(record.getId());
        try {
            executor.execute(() -> executeWithRetry(record, wsTaskId, username, rolesHeader));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            log.warn("Task queue full, rejecting task: dbTaskId={}", record.getId());
            record.setStatus("FAIL");
            record.setErrorMsg("系统繁忙，请稍后重试");
            record.setUpdatedAt(LocalDateTime.now());
            taskRecordMapper.updateById(record);
        }

        return record;
    }

    @Override
    public void submitTaskFromWebSocket(String taskIdStr, String taskType, String input) {
        // If a random mock taskId string was passed from the frontend, create the corresponding DB record
        // and retain the mock taskId for WebSocket progress callbacks
        Long userId = 1L; // Default assign to admin (ID 1) or system user

        TaskRecord record = new TaskRecord();
        // Try parsing taskIdStr as database ID; use if numeric, otherwise auto-generate
        boolean isNumericId = false;
        try {
            Long dbId = Long.parseLong(taskIdStr);
            record.setId(dbId);
            isNumericId = true;
        } catch (NumberFormatException ignored) {}

        // For mock task IDs, let the DB auto-generate the ID; map mock taskId to the auto-generated ID later
        record.setTaskType(taskType != null ? taskType : "TOOL");
        record.setStatus("INIT");
        record.setUserId(userId);
        record.setInput(input);
        record.setAttemptCount(0);
        record.setElapsedTime(0);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());

        if (isNumericId) {
            taskRecordMapper.insert(record);
        } else {
            taskRecordMapper.insert(record);
        }

        log.info("WebSocket triggered task submission: dbTaskId={}, wsTaskId={}", record.getId(), taskIdStr);

        // Execute asynchronously
        try {
            executor.execute(() -> executeWithRetry(record, taskIdStr, "admin", "ROLE_ADMIN"));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            log.warn("Task queue full (WebSocket), rejecting task: dbTaskId={}", record.getId());
            record.setStatus("FAIL");
            record.setErrorMsg("系统繁忙，请稍后重试");
            record.setUpdatedAt(LocalDateTime.now());
            taskRecordMapper.updateById(record);
        }
    }

    @Override
    public TaskRecord getTaskById(Long id) {
        return taskRecordMapper.selectById(id);
    }

    @Override
    public List<TaskRecord> getTaskList(String username) {
        Long userId = getUserIdByUsername(username);
        return taskRecordMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<TaskRecord>lambdaQuery()
                        .eq(TaskRecord::getUserId, userId)
                        .orderByDesc(TaskRecord::getCreatedAt)
        );
    }

    @Override
    public List<TaskRecord> getAllTasks() {
        return taskRecordMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<TaskRecord>lambdaQuery()
                        .orderByDesc(TaskRecord::getCreatedAt)
        );
    }


    private void executeWithRetry(TaskRecord record, String wsTaskId, String username, String rolesHeader) {
        int attempt = record.getAttemptCount() != null ? record.getAttemptCount() : 0;
        Exception lastException = null;

        while (attempt < MAX_RETRY) {
            attempt++;
            record.setAttemptCount(attempt);
            record.setUpdatedAt(LocalDateTime.now());
            taskRecordMapper.updateById(record);

            log.info("Task attempt {}/{}: dbTaskId={}, type={}", attempt, MAX_RETRY, record.getId(), record.getTaskType());

            try {
                executeTaskOnce(record, wsTaskId, username, rolesHeader);
                return; // success
            } catch (Exception e) {
                lastException = e;
                log.warn("Task attempt {}/{} failed: dbTaskId={}, error={}", attempt, MAX_RETRY, record.getId(), e.getMessage());
                if (attempt < MAX_RETRY) {
                    sendProgress(wsTaskId, 5, "running", "Retrying... (" + attempt + "/" + MAX_RETRY + ")");
                    try { Thread.sleep(1000L * attempt); } catch (InterruptedException ignored) {}
                }
            }
        }

        // All retries exhausted
        long endTime = System.currentTimeMillis();
        long startTime = record.getCreatedAt() != null
                ? record.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                : endTime;
        record.setStatus("FAIL");
        record.setErrorMsg(lastException != null ? lastException.getMessage() : "All retries exhausted");
        record.setElapsedTime((int) (endTime - startTime));
        record.setUpdatedAt(LocalDateTime.now());
        taskRecordMapper.updateById(record);
        sendProgress(wsTaskId, 100, "error", "Execution failed after " + MAX_RETRY + " attempts: " +
                (lastException != null ? lastException.getMessage() : "unknown"));
    }

    private void executeTaskOnce(TaskRecord record, String wsTaskId, String username, String rolesHeader) {
        long startTime = System.currentTimeMillis();
        log.info("Starting execution of task: dbTaskId={}, type={}", record.getId(), record.getTaskType());

        // Step 1: Update status to RUNNING
        record.setStatus("RUNNING");
        record.setUpdatedAt(LocalDateTime.now());
        taskRecordMapper.updateById(record);

        // Step 2: Send WebSocket progress - start phase
        sendProgress(wsTaskId, 10, "running", "Analyzing...");

        try {
            String resultOutput = "";
            if ("CODE".equalsIgnoreCase(record.getTaskType())) {
                sendProgress(wsTaskId, 30, "running", "Connecting to Code Agent...");

                // Call Code Agent
                Map<String, String> requestBody = Map.of("question", record.getInput());

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-User-Name", username != null ? username : "anonymousUser");
                headers.set("X-User-Roles", rolesHeader != null ? rolesHeader : "");

                sendProgress(wsTaskId, 50, "running", "Generating SQL, please wait...");
                Map<?, ?> response = restTemplate.postForObject(
                        codeAgentUrl,
                        new HttpEntity<>(requestBody, headers),
                        Map.class
                );

                if (response != null) {
                    resultOutput = objectMapper.writeValueAsString(response);

                    // Check if response contains error field
                    if (response.containsKey("error") && response.get("error") != null && !response.get("error").toString().isEmpty()) {
                        throw new RuntimeException("Code Agent Execution Error: " + response.get("error"));
                    }
                } else {
                    throw new RuntimeException("Received empty response from Code Agent");
                }

            } else if ("TOOL".equalsIgnoreCase(record.getTaskType()) || "AI".equalsIgnoreCase(record.getTaskType())) {
                sendProgress(wsTaskId, 25, "running", "Routing to Tool Agent...");

                // Call Tool Agent
                Map<String, Object> requestBody = Map.of(
                        "toolType", "AI",
                        "naturalLanguage", record.getInput(),
                        "parameters", Map.of()
                );

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-User-Name", username != null ? username : "anonymousUser");
                headers.set("X-User-Roles", rolesHeader != null ? rolesHeader : "");

                sendProgress(wsTaskId, 50, "running", "AI analyzing your request...");
                Map<?, ?> response = restTemplate.postForObject(
                        toolAgentUrl,
                        new HttpEntity<>(requestBody, headers),
                        Map.class
                );
                if (response != null) {
                    Integer code = (Integer) response.get("code");
                    if (code != null && code == 200) {
                        resultOutput = objectMapper.writeValueAsString(response.get("data"));
                    } else {
                        String message = (String) response.get("message");
                        throw new RuntimeException(message != null ? message : "Tool execution failed");
                    }
                } else {
                    throw new RuntimeException("Received empty response from Tool Agent");
                }

            } else if ("RAG".equalsIgnoreCase(record.getTaskType())) {
                sendProgress(wsTaskId, 45, "running", "Retrieving permission-safe document chunks from RAG Agent...");

                Map<String, Object> requestBody = Map.of(
                        "question", record.getInput(),
                        "topK", 5
                );

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-User-Name", username != null ? username : "anonymousUser");
                headers.set("X-User-Roles", rolesHeader != null ? rolesHeader : "");

                sendProgress(wsTaskId, 70, "running", "Generating grounded answer with citations...");
                Map<?, ?> response = restTemplate.postForObject(
                        ragAgentUrl,
                        new HttpEntity<>(requestBody, headers),
                        Map.class
                );

                if (response == null) {
                    throw new RuntimeException("Received empty response from RAG Agent");
                }

                String status = response.get("status") != null ? response.get("status").toString() : "SUCCESS";
                resultOutput = objectMapper.writeValueAsString(response);
                if ("FAIL".equalsIgnoreCase(status)) {
                    Object message = response.get("message");
                    throw new RuntimeException(message != null ? message.toString() : "RAG Agent query failed");
                }
                sendProgress(wsTaskId, 90, "running", "RAG answer generated and audit log recorded.");
            } else {
                throw new RuntimeException("Unsupported task type: " + record.getTaskType());
            }

            sendProgress(wsTaskId, 80, "running", "Processing result...");

            // Step 3. Update status after successful execution
            long endTime = System.currentTimeMillis();
            record.setStatus("SUCCESS");
            record.setOutput(resultOutput);
            record.setElapsedTime((int) (endTime - startTime));
            record.setUpdatedAt(LocalDateTime.now());
            taskRecordMapper.updateById(record);

            log.info("Task completed successfully: dbTaskId={}", record.getId());
            sendProgress(wsTaskId, 100, "completed", "Task completed successfully!");

        } catch (Exception e) {
            log.error("Task execution failed: dbTaskId={}", record.getId(), e);

            // Step 4. Update status after execution failure
            long endTime = System.currentTimeMillis();
            record.setStatus("FAIL");
            record.setErrorMsg(e.getMessage());
            record.setElapsedTime((int) (endTime - startTime));
            record.setUpdatedAt(LocalDateTime.now());
            taskRecordMapper.updateById(record);

            sendProgress(wsTaskId, 100, "error", "Execution failed: " + e.getMessage());
        }
    }

    private void sendProgress(String wsTaskId, int progress, String status, String message) {
        Map<String, Object> progressMap = new HashMap<>();
        progressMap.put("taskId", wsTaskId);
        progressMap.put("progress", progress);
        progressMap.put("status", status);
        progressMap.put("message", message);
        progressMap.put("timestamp", System.currentTimeMillis());
        TaskProgressWebSocketHandler.sendMessage(wsTaskId, progressMap);
    }

    private Long getUserIdByUsername(String username) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM sys_user WHERE username = ? AND deleted = 0",
                    Long.class,
                    username
            );
        } catch (Exception e) {
            log.error("User not found by username: {}. Cannot default to admin — failing.", username);
            throw new RuntimeException("User not found: " + username);
        }
    }
}
