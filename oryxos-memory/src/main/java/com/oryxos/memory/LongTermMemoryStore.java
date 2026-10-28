package com.oryxos.memory;

import java.util.List;

/**
 * 长期记忆存储接口（后端契约）。
 * 所有后端实现（Markdown、SQLite、Mem0）必须遵守同一套三方法契约：
 * ① append 按 Agent 显式选择的 scope 写入；
 * ② load 返回完整 CORE + 窗口 ARCHIVAL；
 * ③ recallByKeyword 只在 ARCHIVAL 中做普通关键词包含匹配。
 */
public interface LongTermMemoryStore {

    /**
     * 追加一条记忆到指定作用域。
     *
     * @param content 记忆内容，非空且非空白
     * @param scope   CORE 或 ARCHIVAL，由 Agent 显式指定
     */
    void append(String content, MemoryScope scope);

    /**
     * 加载长期记忆。
     * CORE 区必须完整返回；ARCHIVAL 区按配置窗口返回最新 N 条。
     * 每次调用都重新读取真实后端，禁止缓存。
     *
     * @return 区分 CORE 与 ARCHIVAL 的记忆内容
     */
    MemoryContent load();

    /**
     * 按关键词在 ARCHIVAL 中检索记忆。
     * 只做普通子串包含匹配，不区分大小写；特殊字符按普通文本处理。
     * CORE 记忆不得返回。
     *
     * @param keyword 关键词，非空且非空白
     * @return 匹配到的完整 ARCHIVAL 记忆条目列表
     */
    List<String> recallByKeyword(String keyword);
}
