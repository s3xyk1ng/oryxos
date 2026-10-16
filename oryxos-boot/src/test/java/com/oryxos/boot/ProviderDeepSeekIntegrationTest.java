package com.oryxos.boot;

import static org.junit.jupiter.api.Assertions.*;

import com.oryxos.core.agent.ProviderService;
import com.oryxos.core.model.Message;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Prompt;
import com.oryxos.core.model.ProviderResponse;
import com.oryxos.storage.entity.LlmCallEntity;
import com.oryxos.storage.repository.LlmCallRepository;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** DeepSeek 集成测试：真模型调用 + 真审计写入。 需要环境变量 DEEPSEEK_API_KEY，标记 @Tag("integration") 正常 CI 跳过。 */
@SpringBootTest(
        properties = {
            "spring.main.web-application-type=none",
            "spring.autoconfigure.exclude=org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration,org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration",
            "management.endpoint.health.group.liveness.include=",
            "management.endpoint.health.group.readiness.include="
        })
@Tag("integration")
class ProviderDeepSeekIntegrationTest {

    @Autowired private ProviderService providerService;

    @Autowired private LlmCallRepository llmCallRepository;

    @BeforeEach
    void cleanAuditTable() {
        llmCallRepository.deleteAll();
    }

    @Test
    @DisplayName("real_deepseek_call：真模型调用返回非空内容")
    void realDeepseekCall() {
        Profile profile = new Profile("test-agent");
        profile.setProvider("deepseek");
        profile.setModel("deepseek-chat");
        profile.setTemperature(0.7);

        Prompt prompt =
                new Prompt(
                        List.of(new Message("user", "Say hello in one word.")),
                        Collections.emptyList());

        ProviderResponse response =
                providerService.chat("session-integration-test", profile, prompt);

        assertNotNull(response, "Response should not be null");
        assertNotNull(response.content(), "Content should not be null");
        assertFalse(response.content().isEmpty(), "Content should not be empty");
        assertNotNull(response.usage(), "Usage should not be null");
        assertTrue(response.usage().totalTokens() > 0, "Total tokens should be > 0");
    }

    @Test
    @DisplayName("real_audit_write：真模型调用后 llm_calls 表有记录")
    void realAuditWrite() {
        Profile profile = new Profile("test-agent");
        profile.setProvider("deepseek");
        profile.setModel("deepseek-chat");
        profile.setTemperature(0.7);

        Prompt prompt = new Prompt(List.of(new Message("user", "Say hi")), Collections.emptyList());

        providerService.chat("session-audit-test", profile, prompt);

        List<LlmCallEntity> records = llmCallRepository.findBySessionId("session-audit-test");
        assertFalse(records.isEmpty(), "llm_calls table should have at least 1 record");

        LlmCallEntity record = records.get(0);
        assertEquals("SUCCESS", record.getStatus());
        assertEquals("deepseek", record.getProvider());
        assertEquals("deepseek-chat", record.getModel());
        assertEquals("test-agent", record.getAgentName());
        assertTrue(record.getPromptTokens() > 0, "Prompt tokens should be > 0");
        assertTrue(record.getDurationMs() > 0, "Duration should be > 0");
    }
}
