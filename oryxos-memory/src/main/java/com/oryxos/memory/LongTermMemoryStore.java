package com.oryxos.memory;

import java.util.List;

/**
 * 长期记忆存储接口。
 * 默认实现为 MarkdownMemoryStore（操作 MEMORY.md），可扩展为 SQLite / Mem0。
 */
public interface LongTermMemoryStore {

    /**
     * 追加一条记忆。
     * @param content 记忆内容
     * @param scope 作用域：CORE（核心记忆，永不截断）或 ARCHIVAL（归档记忆，可截断）
     */
    void append(String content, Scope scope);

    /**
     * 加载全部记忆（核心区全量 + 归档区截断后）。
     */
    MemoryContent load();

    /**
     * 按关键词检索记忆（不区分大小写）。
     */
    List<String> recallByKeyword(String query);

    /**
     * 返回存储能力描述。
     */
    String capabilities();

    enum Scope {
        CORE, ARCHIVAL
    }

    /**
     * 记忆内容（分区）。
     */
    record MemoryContent(String coreSection, String archivalSection) {
        public boolean isEmpty() {
            return (coreSection == null || coreSection.isBlank())
                    && (archivalSection == null || archivalSection.isBlank());
        }

        public String toPromptString() {
            StringBuilder sb = new StringBuilder();
            if (coreSection != null && !coreSection.isBlank()) {
                sb.append("## Core Memory\n").append(coreSection).append("\n\n");
            }
            if (archivalSection != null && !archivalSection.isBlank()) {
                sb.append("## Archival Memory\n").append(archivalSection).append("\n");
            }
            return sb.toString().trim();
        }
    }
}
