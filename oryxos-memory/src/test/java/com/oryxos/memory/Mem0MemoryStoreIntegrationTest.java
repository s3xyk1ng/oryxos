package com.oryxos.memory;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 真实 Mem0 自托管服务集成测试。
 * 需要环境变量 MEM0_BASE_URL 指向可访问的 Mem0 实例，默认跳过。
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "MEM0_BASE_URL", matches = ".+")
class Mem0MemoryStoreIntegrationTest {

    @Test
    void appendAndRecallAgainstRealMem0() {
        String baseUrl = System.getenv("MEM0_BASE_URL");
        String apiKey = System.getenv().getOrDefault("MEM0_API_KEY", "");
        Mem0MemoryStore store = new Mem0MemoryStore(baseUrl, apiKey, 200, "oryxos-integration-test");

        String keyword = "integration-test-" + System.currentTimeMillis();
        store.append("Real Mem0 integration content: " + keyword, MemoryScope.ARCHIVAL);

        var results = store.recallByKeyword(keyword);
        org.junit.jupiter.api.Assertions.assertFalse(results.isEmpty(),
                "Real Mem0 should return the just-appended archival memory");
    }
}
