package com.oryxos.core.scheduler;

import java.time.Instant;
import java.util.List;

/** 定时任务存储接口：隔离核心调度逻辑与持久化实现。 */
public interface ScheduledTaskStore {

    void save(TaskInfo task);

    void retire(String taskKey);

    void recordExecution(
            String taskKey,
            String sessionId,
            String status,
            String result,
            Instant startedAt,
            Instant finishedAt);

    boolean isEnabled(String taskKey);

    void setEnabled(String taskKey, boolean enabled);

    List<TaskInfo> listAll();

    List<ExecutionInfo> executions(String taskKey);

    record TaskInfo(
            String taskKey,
            String profileName,
            String cron,
            String zone,
            String message,
            String displayName,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt) {}

    record ExecutionInfo(
            Integer id,
            String taskKey,
            String sessionId,
            String status,
            String result,
            Instant startedAt,
            Instant finishedAt) {}
}
