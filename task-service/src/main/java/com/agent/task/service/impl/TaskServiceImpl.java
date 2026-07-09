package com.agent.task.service.impl;

import com.agent.task.entity.TaskRecord;
import com.agent.task.handler.TaskProgressWebSocketHandler;
import com.agent.task.mapper.TaskRecordMapper;
import com.agent.task.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRecordMapper taskRecordMapper;
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final String codeAgentUrl;
    private final String toolAgentUrl;

    public TaskServiceImpl(TaskRecordMapper taskRecordMapper, JdbcTemplate jdbcTemplate) {
        this.taskRecordMapper = taskRecordMapper;
        this.jdbcTemplate = jdbcTemplate;
        
        String codeUri = System.getenv("CODE_SERVICE_URI");
        this.codeAgentUrl = (codeUri != null ? codeUri : "http://localhost:8084") + "/code/query";
        
        String toolUri = System.getenv("TOOL_SERVICE_URI");
        this.toolAgentUrl = (toolUri != null ? toolUri : "http://localhost:8083") + "/tool/execute";
    }

    @Override
    public TaskRecord submitTask(String username, String taskType, String input) {
        Long userId = getUserIdByUsername(username);

        // 1. 创建数据库记录
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

        // 2. 异步执行任务
        String wsTaskId = String.valueOf(record.getId());
        executor.execute(() -> executeTaskAsync(record, wsTaskId));

        return record;
    }

    @Override
    public void submitTaskFromWebSocket(String taskIdStr, String taskType, String input) {
        // 如果是前端随机生成的 mock taskId 字符串，我们直接创建对应的 DB 任务，并保留该 mock taskId 用于 WS 回传进度
        Long userId = 1L; // 默认分配给 admin (ID 1) 或者系统用户

        TaskRecord record = new TaskRecord();
        // 尝试解析 taskIdStr 是否为数据库 ID，如果是数字则使用，否则自动生成
        boolean isNumericId = false;
        try {
            Long dbId = Long.parseLong(taskIdStr);
            record.setId(dbId);
            isNumericId = true;
        } catch (NumberFormatException ignored) {}

        // 如果是 mock，不指定 ID 让数据库自增，得到自增 ID 后再和 mock taskId 对应
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

        // 异步执行
        executor.execute(() -> executeTaskAsync(record, taskIdStr));
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


    private void executeTaskAsync(TaskRecord record, String wsTaskId) {
        long startTime = System.currentTimeMillis();
        log.info("Starting execution of task: dbTaskId={}, type={}", record.getId(), record.getTaskType());

        // 1. 更新状态为 RUNNING
        record.setStatus("RUNNING");
        record.setUpdatedAt(LocalDateTime.now());
        taskRecordMapper.updateById(record);

        // 2. 发送 WebSocket 进度 - 开始阶段
        sendProgress(wsTaskId, 20, "running", "Analyzing query and routing to downstream Agent...");

        try {
            String resultOutput = "";
            if ("CODE".equalsIgnoreCase(record.getTaskType())) {
                sendProgress(wsTaskId, 50, "running", "Generating SQL and executing query on database...");
                
                // 调用 Code Agent
                Map<String, String> requestBody = Map.of("question", record.getInput());
                Map<?, ?> response = restTemplate.postForObject(codeAgentUrl, requestBody, Map.class);
                
                if (response != null) {
                    resultOutput = objectMapper.writeValueAsString(response);
                    
                    // 检查响应中是否有错误字段
                    if (response.containsKey("error") && response.get("error") != null && !response.get("error").toString().isEmpty()) {
                        throw new RuntimeException("Code Agent Execution Error: " + response.get("error"));
                    }
                } else {
                    throw new RuntimeException("Received empty response from Code Agent");
                }

            } else if ("TOOL".equalsIgnoreCase(record.getTaskType()) || "AI".equalsIgnoreCase(record.getTaskType())) {
                sendProgress(wsTaskId, 50, "running", "Invoking Tool Agent for intent matching and tool execution...");
                
                // 调用 Tool Agent
                Map<String, Object> requestBody = Map.of(
                        "toolType", "AI",
                        "naturalLanguage", record.getInput(),
                        "parameters", Map.of()
                );
                
                Map<?, ?> response = restTemplate.postForObject(toolAgentUrl, requestBody, Map.class);
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
                throw new RuntimeException("RAG Agent is currently offline (Coming Soon).");
            } else {
                throw new RuntimeException("Unsupported task type: " + record.getTaskType());
            }

            // 3. 执行成功后更新状态
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

            // 4. 执行失败后更新状态
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
            log.warn("User not found by username: {}, defaulting to id=1", username);
            return 1L; // Default fallback to admin (ID 1)
        }
    }
}
