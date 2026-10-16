package com.oryxos.core.react;

/** 记忆端口接口：PromptBuilder 通过此接口获取长期记忆上下文。 实现在 oryxos-memory 模块（MemoryPortImpl）。 */
public interface MemoryPort {

    /** 获取长期记忆上下文，注入到 system prompt 中。 返回空字符串表示无记忆。 */
    String getMemoryContext();
}
