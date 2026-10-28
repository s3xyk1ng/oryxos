package com.oryxos.memory;

import com.oryxos.core.model.Session;
import com.oryxos.core.react.MemoryPort;
import org.springframework.stereotype.Component;

/**
 * MemoryPort 实现：将 core 模块的调用委托给 MemoryService。
 * 只返回 CORE 记忆文本；会话历史由 PromptBuilder 负责，避免重复注入。
 */
@Component
public class MemoryPortImpl implements MemoryPort {

    private final MemoryService memoryService;

    public MemoryPortImpl(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public String buildMemoryContext(Session session) {
        String fullContext = memoryService.buildPromptContext(session);
        // 截取 Core Memory 部分，不含 Conversation History
        int coreStart = fullContext.indexOf("## Core Memory");
        if (coreStart < 0) {
            return "";
        }
        int coreEnd = fullContext.indexOf("## Conversation History", coreStart);
        if (coreEnd < 0) {
            coreEnd = fullContext.length();
        }
        String coreSection = fullContext.substring(coreStart, coreEnd).trim();
        // 去掉 "## Core Memory" 标题，只留内容
        int firstNl = coreSection.indexOf('\n');
        if (firstNl > 0) {
            return coreSection.substring(firstNl + 1).trim();
        }
        return "";
    }
}
