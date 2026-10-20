package com.oryxos.core.agent;

/** 审计端口接口：封装 tool_invocations 和 llm_calls 的写入。 实现在 oryxos-storage 模块。 */
public interface AuditPort {

    /** 记录一次 Tool 调用。 */
    void recordToolInvocation(
            String sessionId,
            String toolCallId,
            String toolName,
            String inputJson,
            String resultJson,
            boolean success,
            String errorMessage,
            long durationMs);

    /** 记录一次 LLM 调用。 */
    void recordLlmCall(
            String sessionId,
            String provider,
            String model,
            int promptTokens,
            int completionTokens,
            long durationMs);
}
