package com.oryxos.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oryxos.core.model.ToolCall;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

class ToolSchemaAdapterTest {

    private final ToolSchemaAdapter adapter = new ToolSchemaAdapter();

    @Test
    @DisplayName("extractToolCalls_empty：无 tool_calls 的响应返回空列表")
    void extractToolCallsEmpty() {
        AssistantMessage msg = new AssistantMessage("Hello", Map.of(), Collections.emptyList());
        Generation gen = new Generation(msg);
        ChatResponse response = new ChatResponse(List.of(gen));

        List<ToolCall> result = adapter.extractToolCalls(response);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("extractToolCalls_multiple：多个 tool_calls 正确提取")
    void extractToolCallsMultiple() {
        List<AssistantMessage.ToolCall> toolCalls =
                List.of(
                        new AssistantMessage.ToolCall(
                                "tc-1", "function", "get_weather", "{\"city\":\"Beijing\"}"),
                        new AssistantMessage.ToolCall(
                                "tc-2", "function", "get_time", "{\"zone\":\"UTC\"}"));
        AssistantMessage msg = new AssistantMessage("", Map.of(), toolCalls);
        Generation gen = new Generation(msg);
        ChatResponse response = new ChatResponse(List.of(gen));

        List<ToolCall> result = adapter.extractToolCalls(response);
        assertEquals(2, result.size());
        assertEquals("get_weather", result.get(0).name());
        assertEquals("{\"city\":\"Beijing\"}", result.get(0).arguments());
        assertEquals("get_time", result.get(1).name());
        assertEquals("{\"zone\":\"UTC\"}", result.get(1).arguments());
    }

    @Test
    @DisplayName("extractContent：正确提取文本内容")
    void extractContentTest() {
        AssistantMessage msg =
                new AssistantMessage("The weather is sunny", Map.of(), Collections.emptyList());
        Generation gen = new Generation(msg);
        ChatResponse response = new ChatResponse(List.of(gen));

        String content = adapter.extractContent(response);
        assertEquals("The weather is sunny", content);
    }
}
