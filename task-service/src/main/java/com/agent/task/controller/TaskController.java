package com.agent.task.controller;

import com.agent.task.dto.Result;
import com.agent.task.dto.TaskSubmitDto;
import com.agent.task.entity.TaskRecord;
import com.agent.task.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/task")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /**
     * Submit an asynchronous task.
     * POST /api/task/submit
     */
    @PostMapping("/submit")
    public Result<TaskRecord> submitTask(
            @RequestHeader(value = "X-User-Name") String username,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles,
            @Valid @RequestBody TaskSubmitDto submitDto) {

        log.info("📩 User '{}' submitted task: type={}, input='{}'", username, submitDto.getTaskType(), submitDto.getInput());
        TaskRecord record = taskService.submitTask(username, roles, submitDto.getTaskType(), submitDto.getInput());
        return Result.success(record);
    }

    /**
     * Query task status and result.
     * GET /api/task/{id}
     */
    @GetMapping("/{id}")
    public Result<TaskRecord> getTask(@PathVariable Long id) {
        log.info("🔍 Querying task status: taskId={}", id);
        TaskRecord record = taskService.getTaskById(id);
        if (record == null) {
            return Result.error(404, "Task not found");
        }
        return Result.success(record);
    }

    /**
     * Query current user's task history.
     * GET /api/task/list
     */
    @GetMapping("/list")
    public Result<List<TaskRecord>> getTaskList(
            @RequestHeader(value = "X-User-Name") String username) {

        log.info("🔍 Querying task history list for user '{}'", username);
        List<TaskRecord> list = taskService.getTaskList(username);
        return Result.success(list);
    }

    /**
     * Admin query for platform-wide task list (requires ROLE_ADMIN).
     * GET /api/task/list/all
     */
    @GetMapping("/list/all")
    public Result<List<TaskRecord>> getAllTasks(
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {

        if (!roles.contains("ROLE_ADMIN")) {
            return Result.error(403, "Access denied: Administrator privileges required.");
        }
        log.info("🔍 Admin querying all tasks");
        List<TaskRecord> list = taskService.getAllTasks();
        return Result.success(list);
    }
}

