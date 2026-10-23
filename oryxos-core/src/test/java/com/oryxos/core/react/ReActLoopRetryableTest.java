package com.oryxos.core.react;

import com.oryxos.core.agent.AuditPort;
import com.oryxos.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReActLoopRetryableTest {

    @Mock PromptBuilder promptBuilder;
    @Mock AuditPort auditPort;
    @Mock LlmPort llmPort;

    private ReActLoop reActLoop;
    private OryxTool mockTool;

    @BeforeEach
    void setUp() {
        when(promptBuilder.buildSystemPrompt(any(), any())).thenReturn("system");
        when(promptBuilder.buildChatPrompt(any(), any(), any())).thenReturn(mock(Prompt.class));
        when(llmPort.getProviderName()).thenReturn("test");
        when(llmPort.getModelName()).thenReturn("test-model");

        mockTool = mock(OryxTool.class);
        when(mockTool.getName()).thenReturn("my_tool");

        ToolExecutor executor = new ToolExecutor(auditPort);
        reActLoop = new ReActLoop(promptBuilder, executor, auditPort);
    }

    @Test
    void retryableSignal_includedInToolMessage() {
        Session session = new Session("s1", "agent1", "cli", "u1");
        Profile profile = new Profile("agent1");

        when(mockTool.execute(any())).thenReturn(ToolResult.fail("timeout", true));

        // Round 1: LLM returns tool call → tool fails with retryable=true
        // Round 2: LLM says stop
        ChatResponse resp1 = buildToolCallResponse("my_tool", Map.of());
        ChatResponse resp2 = buildStopResponse("done");
        when(llmPort.call(any())).thenReturn(resp1, resp2);

        reActLoop.run(session, llmPort, profile, List.of(mockTool), "help");

        boolean foundRetryable = session.getMessages().stream()
                .anyMatch(m -> "tool".equals(m.getRole()) && m.getContent().contains("[retryable]"));
        assertThat(foundRetryable).isTrue();
    }

    @Test
    void notRetryableSignal_includedInToolMessage() {
        Session session = new Session("s1", "agent1", "cli", "u1");
        Profile profile = new Profile("agent1");

        when(mockTool.execute(any())).thenReturn(ToolResult.fail("bad input", false));

        ChatResponse resp1 = buildToolCallResponse("my_tool", Map.of());
        ChatResponse resp2 = buildStopResponse("done");
        when(llmPort.call(any())).thenReturn(resp1, resp2);

        reActLoop.run(session, llmPort, profile, List.of(mockTool), "help");

        boolean foundNotRetryable = session.getMessages().stream()
                .anyMatch(m -> "tool".equals(m.getRole()) && m.getContent().contains("[not retryable]"));
        assertThat(foundNotRetryable).isTrue();
    }

    @Test
    void loopContinues_afterToolFailure() {
        Session session = new Session("s1", "agent1", "cli", "u1");
        Profile profile = new Profile("agent1");

        when(mockTool.execute(any()))
                .thenReturn(ToolResult.fail("fail", true))
                .thenReturn(ToolResult.ok("success"));

        // Round 1: tool call → fails
        ChatResponse resp1 = buildToolCallResponse("my_tool", Map.of());
        // Round 2: tool call → succeeds
        ChatResponse resp2 = buildToolCallResponse("my_tool", Map.of());
        // Round 3: stop
        ChatResponse resp3 = buildStopResponse("done");
        when(llmPort.call(any())).thenReturn(resp1, resp2, resp3);

        reActLoop.run(session, llmPort, profile, List.of(mockTool), "help");

        long toolMessages = session.getMessages().stream()
                .filter(m -> "tool".equals(m.getRole()))
                .count();
        assertThat(toolMessages).isEqualTo(2);
    }

    // --- Helpers ---

    private ChatResponse buildToolCallResponse(String toolName, Map<String, Object> args) {
        String argsJson;
        try {
            argsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(args);
        } catch (Exception e) {
            argsJson = "{}";
        }
        var toolCall = new AssistantMessage.ToolCall("tc1", "function", toolName, argsJson);
        var assistantMsg = new AssistantMessage("", Map.of(), List.of(toolCall));
        return new ChatResponse(List.of(new Generation(assistantMsg)));
    }

    private ChatResponse buildStopResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
