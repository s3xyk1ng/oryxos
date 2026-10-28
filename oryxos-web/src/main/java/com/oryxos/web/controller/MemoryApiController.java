package com.oryxos.web.controller;

import com.oryxos.memory.LongTermMemoryStore;
import com.oryxos.memory.MemoryContent;
import com.oryxos.web.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

/**
 * Memory API：查询长期记忆。
 */
@RestController
@RequestMapping("/api/v1/memory")
public class MemoryApiController {

    private final LongTermMemoryStore longTermMemoryStore;

    public MemoryApiController(LongTermMemoryStore longTermMemoryStore) {
        this.longTermMemoryStore = longTermMemoryStore;
    }

    @GetMapping
    public ApiResponse<MemoryContent> getMemory() {
        return ApiResponse.ok(longTermMemoryStore.load());
    }
}
