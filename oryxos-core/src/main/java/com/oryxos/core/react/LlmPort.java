package com.oryxos.core.react;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/** LLM 调用端口：ReAct 循环通过此接口调用 LLM，不直接依赖 Provider 模块。 */
public interface LlmPort {

    /** 调用 LLM。 */
    ChatResponse call(Prompt prompt);

    /** 获取 Provider 名称。 */
    String getProviderName();

    /** 获取模型名称。 */
    String getModelName();
}
