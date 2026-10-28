package com.oryxos.core.react;

import com.oryxos.core.model.Session;

/**
 * 记忆端口接口：PromptBuilder 通过此接口获取核心记忆上下文。
 * 实现在 oryxos-memory 模块（MemoryPortImpl）。
 * 注意：本接口只返回 CORE 记忆；会话历史仍由 PromptBuilder 负责拼接，避免重复注入。
 */
public interface MemoryPort {

    /**
     * 获取核心记忆上下文，注入到 system prompt 中。
     * 返回空字符串表示无核心记忆。
     *
     * @param session 当前会话
     * @return 核心记忆文本
     */
    String buildMemoryContext(Session session);
}
