package com.oryxos.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.model.Message;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Prompt;
import com.oryxos.core.model.ProviderDef;
import com.oryxos.core.model.ProviderResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/** 集成冒烟测试：验证 Provider 模块端到端流程。 */
@Tag("integration")
class ProviderSmokeTest {

    @Test
    @DisplayName("smoke_full_flow：端到端流程跑通")
    void smokeFullFlow() {
        // 1. Registry 注册
        InMemoryProviderRegistry registry = new InMemoryProviderRegistry();
        ProviderDef def = new ProviderDef("smoke-provider", "openai", "sk-smoke", null, null);
        registry.save(def);
        assertNotNull(registry.find("smoke-provider"));

        // 2. ChatModel mock
        ChatModel mockChatModel = mock(ChatModel.class);
        AssistantMessage msg =
                new AssistantMessage("Smoke test OK", Map.of(), Collections.emptyList());
        Generation gen = new Generation(msg);
        ChatResponseMetadata metadata =
                ChatResponseMetadata.builder().usage(new DefaultUsage(5, 10)).build();
        ChatResponse mockResponse = new ChatResponse(List.of(gen), metadata);
        when(mockChatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(mockResponse);

        // 3. 组装 Service
        ProviderChatModelFactory factory = mock(ProviderChatModelFactory.class);
        when(factory.resolve(any(ProviderDef.class))).thenReturn(mockChatModel);
        ToolSchemaAdapter adapter = new ToolSchemaAdapter();
        AuditLogger auditLogger = mock(AuditLogger.class);
        SpringAiProviderServiceImpl service =
                new SpringAiProviderServiceImpl(registry, factory, adapter, auditLogger);

        // 4. 调用
        Profile profile = new Profile("smoke-agent");
        profile.setProvider("smoke-provider");
        profile.setModel("gpt-4o-mini");
        profile.setTemperature(0.5);
        Prompt prompt = new Prompt(List.of(new Message("user", "Hello")), Collections.emptyList());
        ProviderResponse result = service.chat("session-smoke", profile, prompt);

        // 5. 验证
        assertNotNull(result);
        assertEquals("Smoke test OK", result.content());
        verify(auditLogger).logSuccess(any(), any(), any(), any(), any(), anyInt(), anyLong());
    }
}
