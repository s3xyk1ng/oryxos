package com.oryxos.memory;

/**
 * 长期记忆加载结果，区分核心记忆与归档记忆。
 * 空存储时返回空字符串，不返回 null。
 */
public record MemoryContent(String coreSection, String archivalSection) {

    public MemoryContent {
        coreSection = coreSection == null ? "" : coreSection;
        archivalSection = archivalSection == null ? "" : archivalSection;
    }

    public boolean isEmpty() {
        return coreSection.isBlank() && archivalSection.isBlank();
    }
}
