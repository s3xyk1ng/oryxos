package com.oryxos.core.agent;

import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Prompt;
import com.oryxos.core.model.ProviderResponse;

/** Provider 对外唯一门面。ReAct 循环通过此接口调用 LLM， 不直接依赖具体 Provider 实现。 */
public interface ProviderService {

    /**
     * 调用 LLM。
     *
     * @param sessionId 会话标识
     * @param profile 当前 Agent 的 Profile（含 provider name / model / temperature）
     * @param prompt 请求（消息列表 + 可用工具列表）
     * @return LLM 响应（文本内容 + 原始 tool_calls + token 用量）
     */
    ProviderResponse chat(String sessionId, Profile profile, Prompt prompt);
}
