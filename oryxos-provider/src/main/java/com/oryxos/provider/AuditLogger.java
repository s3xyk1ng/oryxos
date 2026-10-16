package com.oryxos.provider;

import com.oryxos.core.model.Usage;
import com.oryxos.storage.entity.LlmCallEntity;
import com.oryxos.storage.repository.LlmCallRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** LLM 调用审计记录器。每次调用（成功/失败）写入 llm_calls 表。 */
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    private final LlmCallRepository repository;

    public AuditLogger(LlmCallRepository repository) {
        this.repository = repository;
    }

    /** 记录成功的 LLM 调用。 */
    public void logSuccess(
            String sessionId,
            String agentName,
            String providerName,
            String model,
            Usage usage,
            int toolCallCount,
            long durationMs) {
        LlmCallEntity entity =
                createBaseEntity(sessionId, agentName, providerName, model, durationMs);
        entity.setStatus("SUCCESS");
        entity.setPromptTokens((int) usage.promptTokens());
        entity.setCompletionTokens((int) usage.completionTokens());
        entity.setTotalTokens((int) usage.totalTokens());
        entity.setToolCallCount(toolCallCount);
        repository.save(entity);
        log.debug(
                "Audit: LLM call SUCCESS session={} provider={} model={} tokens={}",
                sessionId,
                providerName,
                model,
                usage.totalTokens());
    }

    /** 记录失败的 LLM 调用。 */
    public void logFailure(
            String sessionId,
            String agentName,
            String providerName,
            String model,
            long durationMs,
            String errorMessage) {
        LlmCallEntity entity =
                createBaseEntity(sessionId, agentName, providerName, model, durationMs);
        entity.setStatus("FAILED");
        entity.setErrorMessage(truncate(errorMessage, 1000));
        repository.save(entity);
        log.debug(
                "Audit: LLM call FAILED session={} provider={} model={} error={}",
                sessionId,
                providerName,
                model,
                errorMessage);
    }

    private LlmCallEntity createBaseEntity(
            String sessionId,
            String agentName,
            String providerName,
            String model,
            long durationMs) {
        LlmCallEntity entity = new LlmCallEntity();
        entity.setSessionId(sessionId);
        entity.setAgentName(agentName);
        entity.setProvider(providerName);
        entity.setModel(model);
        entity.setDurationMs(durationMs);
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen);
    }
}
