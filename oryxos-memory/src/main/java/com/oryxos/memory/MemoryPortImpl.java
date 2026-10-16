package com.oryxos.memory;

import com.oryxos.core.react.MemoryPort;
import org.springframework.stereotype.Component;

/** MemoryPort 实现：委托 MemoryService 获取长期记忆上下文。 */
@Component
public class MemoryPortImpl implements MemoryPort {

    private final MemoryService memoryService;

    public MemoryPortImpl(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public String getMemoryContext() {
        LongTermMemoryStore.MemoryContent memory = memoryService.getLongTermMemory();
        if (memory.isEmpty()) {
            return "";
        }
        return memory.toPromptString();
    }
}
