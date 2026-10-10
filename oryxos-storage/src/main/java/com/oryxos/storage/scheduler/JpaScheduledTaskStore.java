package com.oryxos.storage.scheduler;

import com.oryxos.core.scheduler.ScheduledTaskStore;
import com.oryxos.storage.entity.ScheduledTaskEntity;
import com.oryxos.storage.entity.TaskExecutionEntity;
import com.oryxos.storage.repository.ScheduledTaskRepository;
import com.oryxos.storage.repository.TaskExecutionRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class JpaScheduledTaskStore implements ScheduledTaskStore {

    private final ScheduledTaskRepository taskRepo;
    private final TaskExecutionRepository execRepo;

    public JpaScheduledTaskStore(ScheduledTaskRepository taskRepo, TaskExecutionRepository execRepo) {
        this.taskRepo = taskRepo;
        this.execRepo = execRepo;
    }

    @Override
    public void save(TaskInfo task) {
        ScheduledTaskEntity entity = taskRepo.findByTaskKey(task.taskKey())
                .orElse(new ScheduledTaskEntity());
        entity.setTaskKey(task.taskKey());
        entity.setProfileName(task.profileName());
        entity.setCron(task.cron());
        entity.setZone(task.zone());
        entity.setMessage(task.message());
        entity.setDisplayName(task.displayName());
        entity.setEnabled(task.enabled());
        if (entity.getCreatedAt() == null) entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        taskRepo.save(entity);
    }

    @Override
    public void retire(String taskKey) {
        taskRepo.findByTaskKey(taskKey).ifPresent(e -> {
            e.setEnabled(false);
            e.setUpdatedAt(Instant.now());
            taskRepo.save(e);
        });
    }

    @Override
    public void recordExecution(String taskKey, String sessionId, String status,
                                String result, Instant startedAt, Instant finishedAt) {
        TaskExecutionEntity entity = new TaskExecutionEntity();
        entity.setTaskKey(taskKey);
        entity.setSessionId(sessionId);
        entity.setStatus(status);
        entity.setResult(result);
        entity.setStartedAt(startedAt);
        entity.setFinishedAt(finishedAt);
        execRepo.save(entity);
    }

    @Override
    public boolean isEnabled(String taskKey) {
        return taskRepo.findByTaskKey(taskKey)
                .map(ScheduledTaskEntity::isEnabled)
                .orElse(false);
    }

    @Override
    public void setEnabled(String taskKey, boolean enabled) {
        taskRepo.findByTaskKey(taskKey).ifPresent(e -> {
            e.setEnabled(enabled);
            e.setUpdatedAt(Instant.now());
            taskRepo.save(e);
        });
    }

    @Override
    public List<TaskInfo> listAll() {
        return taskRepo.findAll().stream()
                .map(e -> new TaskInfo(e.getTaskKey(), e.getProfileName(), e.getCron(),
                        e.getZone(), e.getMessage(), e.getDisplayName(), e.isEnabled(),
                        e.getCreatedAt(), e.getUpdatedAt()))
                .toList();
    }

    @Override
    public List<ExecutionInfo> executions(String taskKey) {
        return execRepo.findByTaskKeyOrderByStartedAtDesc(taskKey).stream()
                .map(e -> new ExecutionInfo(e.getId(), e.getTaskKey(), e.getSessionId(),
                        e.getStatus(), e.getResult(), e.getStartedAt(), e.getFinishedAt()))
                .toList();
    }
}
