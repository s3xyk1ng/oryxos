package com.oryxos.web.controller;

import com.oryxos.core.scheduler.AgentScheduler;
import com.oryxos.core.scheduler.ScheduledTaskStore;
import com.oryxos.web.dto.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** 定时任务管理 API (v2)。 */
@RestController
public class ScheduleApiController {

    private final ScheduledTaskStore taskStore;
    private final AgentScheduler scheduler;

    public ScheduleApiController(ScheduledTaskStore taskStore, AgentScheduler scheduler) {
        this.taskStore = taskStore;
        this.scheduler = scheduler;
    }

    @GetMapping("/api/v2/schedules")
    public ApiResponse<java.util.List<ScheduledTaskStore.TaskInfo>> listSchedules() {
        return ApiResponse.ok(taskStore.listAll());
    }

    @GetMapping("/api/v2/schedules/{taskKey}/executions")
    public ApiResponse<java.util.List<ScheduledTaskStore.ExecutionInfo>> getExecutions(
            @PathVariable String taskKey) {
        return ApiResponse.ok(taskStore.executions(taskKey));
    }

    @PostMapping("/api/v2/schedules/{taskKey}/run")
    public ApiResponse<String> runNow(@PathVariable String taskKey) {
        var tasks = taskStore.listAll();
        var task = tasks.stream().filter(t -> t.taskKey().equals(taskKey)).findFirst();
        if (task.isEmpty()) {
            throw new com.oryxos.web.GlobalExceptionHandler.ResourceNotFoundException(
                    "Task not found: " + taskKey);
        }
        scheduler.runNow(taskKey, task.get().profileName(), task.get().message());
        return ApiResponse.ok("Task executed: " + taskKey);
    }

    @PutMapping("/api/v2/schedules/{taskKey}")
    public ApiResponse<String> updateSchedule(
            @PathVariable String taskKey, @RequestBody Map<String, Object> body) {
        Object enabled = body.get("enabled");
        if (enabled instanceof Boolean b) {
            scheduler.setEnabled(taskKey, b);
            return ApiResponse.ok("Task " + taskKey + " " + (b ? "enabled" : "disabled"));
        }
        return ApiResponse.error(400, "enabled field is required");
    }

    @PostMapping("/api/v2/agents/{name}/schedules/{key}/run")
    public ApiResponse<String> runAgentSchedule(
            @PathVariable String name, @PathVariable String key) {
        String taskKey = name + ":" + key;
        scheduler.runNow(taskKey, name, "Execute scheduled task");
        return ApiResponse.ok("Agent schedule executed: " + taskKey);
    }
}
