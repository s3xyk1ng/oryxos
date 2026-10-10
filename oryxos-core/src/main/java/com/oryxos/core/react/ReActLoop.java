package com.oryxos.core.react;

import com.oryxos.core.agent.AuditPort;
import com.oryxos.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ReAct 循环：Agent 大脑。
 * 流程：组装 Prompt → 调用 LLM → 解析 tool calls → 执行 Tool → 追加结果 → 循环。
 * 无 tool call 时终止，达到 MAX_ITERATIONS 时强制终止。
 */
@Component
public class ReActLoop {

    private static final Logger log = LoggerFactory.getLogger(ReActLoop.class);

    private final PromptBuilder promptBuilder;
    private final ToolExecutor toolExecutor;
    private final AuditPort auditPort;

    public ReActLoop(PromptBuilder promptBuilder, ToolExecutor toolExecutor, AuditPort auditPort) {
        this.promptBuilder = promptBuilder;
        this.toolExecutor = toolExecutor;
        this.auditPort = auditPort;
    }

    /**
     * 执行 ReAct 循环。
     */
    public String run(Session session, LlmPort llm, Profile profile,
                      List<OryxTool> tools, String userMessage) {
        int maxIterations = profile.getMaxIterations();
        session.addMessage(new Message("user", userMessage));

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            log.debug("ReAct iteration {}/{} for session {}", iteration + 1, maxIterations, session.getSessionId());

            // Build prompt
            String systemPrompt = promptBuilder.buildSystemPrompt(profile, session);
            Prompt prompt = promptBuilder.buildChatPrompt(systemPrompt, session.getMessages(), tools);

            // Call LLM
            long llmStart = System.currentTimeMillis();
            ChatResponse response = llm.call(prompt);
            long llmDuration = System.currentTimeMillis() - llmStart;

            // Record LLM audit
            recordLlmAudit(session, llm, response, llmDuration);

            String assistantContent = response.getResult().getOutput().getText();
            session.addMessage(new Message("assistant", assistantContent));

            // Extract tool calls
            List<ToolCallRequest> toolCalls = ToolCallParser.extract(response);
            if (toolCalls.isEmpty()) {
                log.debug("No tool calls at iteration {}, loop ends", iteration + 1);
                return assistantContent;
            }

            // Execute each tool call
            for (ToolCallRequest tc : toolCalls) {
                log.debug("Executing tool: {} (iteration {})", tc.getToolName(), iteration + 1);
                ToolResult result = toolExecutor.execute(session, tc, tools);
                String resultContent = result.isSuccess() ? result.getContent() : "Error: " + result.getError();
                session.addMessage(new Message("tool", "[" + tc.getToolName() + "] " + resultContent));
            }
        }

        // Max iterations reached
        log.warn("Max iterations ({}) reached for session {}", maxIterations, session.getSessionId());
        List<Message> messages = session.getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("assistant".equals(messages.get(i).getRole())) {
                return messages.get(i).getContent();
            }
        }
        return "I've reached the maximum number of steps.";
    }

    private void recordLlmAudit(Session session, LlmPort llm, ChatResponse response, long duration) {
        try {
            var metadata = response.getMetadata();
            var usage = metadata.getUsage();
            int promptTokens = usage != null ? (int) usage.getPromptTokens() : 0;
            int completionTokens = usage != null ? (int) usage.getCompletionTokens() : 0;
            auditPort.recordLlmCall(session.getSessionId(), llm.getProviderName(),
                    llm.getModelName(), promptTokens, completionTokens, duration);
        } catch (Exception e) {
            log.warn("Failed to record LLM audit: {}", e.getMessage());
        }
    }
}
