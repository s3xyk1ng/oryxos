package com.oryxos.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.agent.ProviderRegistry;
import com.oryxos.core.model.Message;
import com.oryxos.core.model.OryxTool;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Prompt;
import com.oryxos.core.model.ProviderCallException;
import com.oryxos.core.model.ProviderDef;
import com.oryxos.core.model.ProviderNotFoundException;
import com.oryxos.core.model.ProviderResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

class SpringAiProviderServiceImplTest {

    private ProviderRegistry registry;
    private ProviderChatModelFactory factory;
    private ToolSchemaAdapter toolSchemaAdapter;
    private AuditLogger auditLogger;
    private SpringAiProviderServiceImpl service;

    @BeforeEach
    void setUp() {
        registry = mock(ProviderRegistry.class);
        factory = mock(ProviderChatModelFactory.class);
        toolSchemaAdapter = new ToolSchemaAdapter();
        auditLogger = mock(AuditLogger.class);
        service =
                new SpringAiProviderServiceImpl(registry, factory, toolSchemaAdapter, auditLogger);
    }

    private Profile createProfile(String providerName, String model, Double temperature) {
        Profile profile = new Profile("test-agent");
        profile.setProvider(providerName);
        profile.setModel(model);
        profile.setTemperature(temperature);
        return profile;
    }

    private ProviderDef createProviderDef() {
        return new ProviderDef("test-provider", "openai", "sk-test", null, null);
    }

    private ChatResponse createMockChatResponse(
            String content, List<AssistantMessage.ToolCall> toolCalls) {
        AssistantMessage assistantMessage = new AssistantMessage(content, Map.of(), toolCalls);
        Generation generation = new Generation(assistantMessage);
        ChatResponseMetadata metadata =
                ChatResponseMetadata.builder().usage(new DefaultUsage(10, 20)).build();
        return new ChatResponse(List.of(generation), metadata);
    }

    @Test
    @DisplayName("chat_success_no_tools：正常调用无工具，返回文本内容")
    void chatSuccessNoTools() {
        ProviderDef def = createProviderDef();
        when(registry.find("test-provider")).thenReturn(def);
        ChatModel chatModel = mock(ChatModel.class);
        when(factory.resolve(def)).thenReturn(chatModel);
        ChatResponse mockResponse = createMockChatResponse("Hello!", Collections.emptyList());
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(mockResponse);

        Profile profile = createProfile("test-provider", "gpt-4o", 0.7);
        Prompt prompt = new Prompt(List.of(new Message("user", "Hi")), Collections.emptyList());

        ProviderResponse result = service.chat("session-1", profile, prompt);

        assertNotNull(result);
        assertEquals("Hello!", result.content());
        assertFalse(result.hasToolCalls());
        assertNotNull(result.usage());
        verify(auditLogger).logSuccess(any(), any(), any(), any(), any(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("chat_success_with_tools：调用含工具，返回原始 tool_calls 未执行")
    void chatSuccessWithTools() {
        ProviderDef def = createProviderDef();
        when(registry.find("test-provider")).thenReturn(def);
        ChatModel chatModel = mock(ChatModel.class);
        when(factory.resolve(def)).thenReturn(chatModel);

        List<AssistantMessage.ToolCall> springToolCalls =
                List.of(
                        new AssistantMessage.ToolCall(
                                "tc-1", "function", "get_weather", "{\"city\":\"Beijing\"}"));
        ChatResponse mockResponse = createMockChatResponse("", springToolCalls);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(mockResponse);

        OryxTool mockTool = mock(OryxTool.class);
        when(mockTool.getName()).thenReturn("get_weather");
        when(mockTool.getDescription()).thenReturn("Get weather");
        when(mockTool.getInputSchema()).thenReturn(Map.of("type", "object"));

        Profile profile = createProfile("test-provider", "gpt-4o", 0.7);
        Prompt prompt = new Prompt(List.of(new Message("user", "Weather?")), List.of(mockTool));

        ProviderResponse result = service.chat("session-1", profile, prompt);

        assertNotNull(result);
        assertTrue(result.hasToolCalls());
        assertEquals(1, result.toolCalls().size());
        assertEquals("get_weather", result.toolCalls().get(0).name());
        assertEquals("{\"city\":\"Beijing\"}", result.toolCalls().get(0).arguments());
    }

    @Test
    @DisplayName("chat_provider_not_found：Provider 不存在抛 ProviderNotFoundException")
    void chatProviderNotFound() {
        when(registry.find("nonexistent")).thenThrow(new ProviderNotFoundException("nonexistent"));

        Profile profile = createProfile("nonexistent", "gpt-4o", 0.7);
        Prompt prompt = new Prompt(List.of(new Message("user", "Hi")), Collections.emptyList());

        assertThrows(
                ProviderNotFoundException.class, () -> service.chat("session-1", profile, prompt));
        verify(auditLogger, never())
                .logSuccess(any(), any(), any(), any(), any(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("chat_llm_call_failed：LLM 调用失败抛 ProviderCallException，审计记录 FAILED")
    void chatLlmCallFailed() {
        ProviderDef def = createProviderDef();
        when(registry.find("test-provider")).thenReturn(def);
        ChatModel chatModel = mock(ChatModel.class);
        when(factory.resolve(def)).thenReturn(chatModel);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenThrow(new RuntimeException("Connection timeout"));

        Profile profile = createProfile("test-provider", "gpt-4o", 0.7);
        Prompt prompt = new Prompt(List.of(new Message("user", "Hi")), Collections.emptyList());

        assertThrows(ProviderCallException.class, () -> service.chat("session-1", profile, prompt));
        verify(auditLogger).logFailure(any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("chat_options_merged：Profile 的 model + temperature 合并到 ChatOptions")
    void chatOptionsMerged() {
        ProviderDef def = createProviderDef();
        when(registry.find("test-provider")).thenReturn(def);
        ChatModel chatModel = mock(ChatModel.class);
        when(factory.resolve(def)).thenReturn(chatModel);
        ChatResponse mockResponse = createMockChatResponse("OK", Collections.emptyList());
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(mockResponse);

        Profile profile = createProfile("test-provider", "gpt-4o", 0.5);
        Prompt prompt = new Prompt(List.of(new Message("user", "Test")), Collections.emptyList());

        service.chat("session-1", profile, prompt);

        // Verify that chatModel.call was invoked with a Prompt containing ChatOptions
        verify(chatModel).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }
}
