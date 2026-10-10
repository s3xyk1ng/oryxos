package com.oryxos.web.controller;

import com.oryxos.memory.LongTermMemoryStore;
import com.oryxos.memory.MemoryService;
import com.oryxos.web.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

/**
 * Memory API：查询长期记忆。
 */
@RestController
@RequestMapping("/api/v1/memory")
public class MemoryApiController {

    private final MemoryService memoryService;

    public MemoryApiController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping
    public ApiResponse<LongTermMemoryStore.MemoryContent> getMemory() {
        return ApiResponse.ok(memoryService.getLongTermMemory());
    }
}
