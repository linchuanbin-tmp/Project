package com.agent.task.service;

import com.agent.task.entity.TaskRecord;
import java.util.List;

public interface TaskService {
    TaskRecord submitTask(String username, String taskType, String input);
    void submitTaskFromWebSocket(String taskIdStr, String taskType, String input);
    TaskRecord getTaskById(Long id);
    List<TaskRecord> getTaskList(String username);
    List<TaskRecord> getAllTasks();
}
