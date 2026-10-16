package com.oryxos.storage.audit;

import com.oryxos.core.agent.AuditPort;
import com.oryxos.storage.entity.LlmCallEntity;
import com.oryxos.storage.entity.ToolInvocationEntity;
import com.oryxos.storage.repository.LlmCallRepository;
import com.oryxos.storage.repository.ToolInvocationRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 审计服务实现：将审计数据写入 tool_invocations 和 llm_calls 表。 */
@Service
public class AuditServiceImpl implements AuditPort {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final ToolInvocationRepository toolInvocationRepo;
    private final LlmCallRepository llmCallRepo;

    public AuditServiceImpl(
            ToolInvocationRepository toolInvocationRepo, LlmCallRepository llmCallRepo) {
        this.toolInvocationRepo = toolInvocationRepo;
        this.llmCallRepo = llmCallRepo;
    }

    @Override
    public void recordToolInvocation(
            String sessionId,
            String toolName,
            String inputJson,
            String resultJson,
            boolean success,
            String errorMessage,
            long durationMs) {
        try {
            ToolInvocationEntity entity = new ToolInvocationEntity();
            entity.setSessionId(sessionId);
            entity.setToolName(toolName);
            entity.setInputJson(inputJson);
            entity.setResultJson(resultJson);
            entity.setSuccess(success);
            entity.setErrorMessage(errorMessage);
            entity.setDurationMs(durationMs);
            entity.setCreatedAt(Instant.now());
            toolInvocationRepo.save(entity);
        } catch (Exception e) {
            log.error("Failed to record tool invocation audit: {}", e.getMessage());
        }
    }

    @Override
    public void recordLlmCall(
            String sessionId,
            String provider,
            String model,
            int promptTokens,
            int completionTokens,
            long durationMs) {
        try {
            LlmCallEntity entity = new LlmCallEntity();
            entity.setSessionId(sessionId);
            entity.setProvider(provider);
            entity.setModel(model);
            entity.setPromptTokens(promptTokens);
            entity.setCompletionTokens(completionTokens);
            entity.setTotalTokens(promptTokens + completionTokens);
            entity.setDurationMs(durationMs);
            entity.setCreatedAt(Instant.now());
            llmCallRepo.save(entity);
        } catch (Exception e) {
            log.error("Failed to record LLM call audit: {}", e.getMessage());
        }
    }
}
