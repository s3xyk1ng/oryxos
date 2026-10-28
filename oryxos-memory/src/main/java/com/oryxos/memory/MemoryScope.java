package com.oryxos.memory;

/**
 * 记忆作用域。
 * 只有 CORE 和 ARCHIVAL 两个值，不允许自动、临时或推断的作用域。
 */
public enum MemoryScope {

    /**
     * 核心记忆：每轮 Prompt 都自动完整注入，永不截断，不参与 recall。
     */
    CORE,

    /**
     * 归档记忆：不自动注入 Prompt，仅在 Agent 调用 recall_memory 时按关键词召回。
     */
    ARCHIVAL
}
