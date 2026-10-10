package com.oryxos.core.scheduler;

import com.oryxos.core.agent.AgentService;
import com.oryxos.core.agent.ProfileRegistry;
import com.oryxos.core.agent.SessionManager;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Agent 定时调度器：基于 Spring TaskScheduler + CronTrigger 动态注册任务。
 * 启动时扫描所有 Profile 的 schedules 字段，注册定时任务。
 * 进程内 ReentrantLock 防重叠执行。
 */
@Component
public class AgentScheduler {

    private static final Logger log = LoggerFactory.getLogger(AgentScheduler.class);

    private final ProfileRegistry profileRegistry;
    private final AgentService agentService;
    private final SessionManager sessionManager;
    private final ScheduledTaskStore taskStore;

    private TaskScheduler taskScheduler;
    private final Map<String, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> taskLocks = new ConcurrentHashMap<>();

    public AgentScheduler(ProfileRegistry profileRegistry, AgentService agentService,
                          SessionManager sessionManager, ScheduledTaskStore taskStore) {
        this.profileRegistry = profileRegistry;
        this.agentService = agentService;
        this.sessionManager = sessionManager;
        this.taskStore = taskStore;
    }

    @PostConstruct
    public void init() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("agent-scheduler-");
        scheduler.initialize();
        this.taskScheduler = scheduler;

        // Register tasks from profiles
        for (Profile profile : profileRegistry.getAll()) {
            if (profile.getSchedules() == null) continue;
            for (Profile.ScheduleConfig sc : profile.getSchedules()) {
                registerTask(profile.getName(), sc);
            }
        }
        log.info("AgentScheduler initialized with {} tasks", scheduledFutures.size());
    }

    public void registerTask(String profileName, Profile.ScheduleConfig config) {
        String taskKey = profileName + ":" + config.getKey();
        try {
            CronTrigger trigger = new CronTrigger(config.getCron(),
                    TimeZone.getTimeZone(config.getZone()));
            ReentrantLock lock = taskLocks.computeIfAbsent(taskKey, k -> new ReentrantLock());

            ScheduledFuture<?> future = taskScheduler.schedule(() -> {
                if (!lock.tryLock()) {
                    log.warn("Task {} still running, skipping", taskKey);
                    return;
                }
                try {
                    executeTask(taskKey, profileName, config.getMessage());
                } finally {
                    lock.unlock();
                }
            }, trigger);

            scheduledFutures.put(taskKey, future);

            // Persist to store
            taskStore.save(new ScheduledTaskStore.TaskInfo(
                    taskKey, profileName, config.getCron(), config.getZone(),
                    config.getMessage(), config.getDisplayName(), true,
                    Instant.now(), Instant.now()));

            log.info("Registered scheduled task: {} (cron: {})", taskKey, config.getCron());
        } catch (Exception e) {
            log.error("Failed to register task {}: {}", taskKey, e.getMessage());
        }
    }

    public void setEnabled(String taskKey, boolean enabled) {
        taskStore.setEnabled(taskKey, enabled);
        if (!enabled) {
            ScheduledFuture<?> future = scheduledFutures.remove(taskKey);
            if (future != null) {
                future.cancel(false);
                log.info("Disabled task: {}", taskKey);
            }
        }
    }

    public void runNow(String taskKey, String profileName, String message) {
        executeTask(taskKey, profileName, message);
    }

    private void executeTask(String taskKey, String profileName, String message) {
        Instant start = Instant.now();
        String sessionId = SessionManager.generateSessionId("scheduler", "scheduler", profileName);
        Session session = sessionManager.getOrCreateSession("scheduler", "scheduler", profileName);
        try {
            String result = agentService.process(session, message != null ? message : "Execute scheduled task");
            taskStore.recordExecution(taskKey, sessionId, "SUCCESS", result, start, Instant.now());
        } catch (Exception e) {
            log.error("Scheduled task {} failed: {}", taskKey, e.getMessage());
            taskStore.recordExecution(taskKey, sessionId, "FAILED", e.getMessage(), start, Instant.now());
        }
    }
}
