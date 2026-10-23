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
class ReActLoopIsolationTest {

    @Mock PromptBuilder promptBuilder;
    @Mock AuditPort auditPort;
    @Mock LlmPort llmPort;

    private ReActLoop reActLoop;
    private OryxTool toolA;
    private OryxTool toolB;

    @BeforeEach
    void setUp() {
        when(promptBuilder.buildSystemPrompt(any(), any())).thenReturn("system");
        when(promptBuilder.buildChatPrompt(any(), any(), any())).thenReturn(mock(Prompt.class));
        when(llmPort.getProviderName()).thenReturn("test");
        when(llmPort.getModelName()).thenReturn("test-model");

        toolA = mock(OryxTool.class);
        toolB = mock(OryxTool.class);
        when(toolA.getName()).thenReturn("tool_a");
        when(toolB.getName()).thenReturn("tool_b");

        ToolExecutor executor = new ToolExecutor(auditPort);
        reActLoop = new ReActLoop(promptBuilder, executor, auditPort);
    }

    @Test
    void sameBatch_oneFails_otherStillExecutes() {
        Session session = new Session("s1", "agent1", "cli", "u1");
        Profile profile = new Profile("agent1");

        when(toolA.execute(any())).thenThrow(new RuntimeException("tool_a crashed"));
        when(toolB.execute(any())).thenReturn(ToolResult.ok("tool_b ok"));

        // Round 1: two tool calls in same batch
        ChatResponse resp1 = buildBatchToolCallResponse(
                new String[]{"tool_a", "tool_b"},
                new Map[]{Map.of(), Map.of()}
        );
        ChatResponse resp2 = buildStopResponse("done");
        when(llmPort.call(any())).thenReturn(resp1, resp2);

        reActLoop.run(session, llmPort, profile, List.of(toolA, toolB), "help");

        verify(toolA).execute(any());
        verify(toolB).execute(any());

        long toolMessages = session.getMessages().stream()
                .filter(m -> "tool".equals(m.getRole()))
                .count();
        assertThat(toolMessages).isEqualTo(2);
    }

    @Test
    void crossTurn_oneFails_nextTurnStillRuns() {
        Session session = new Session("s1", "agent1", "cli", "u1");
        Profile profile = new Profile("agent1");

        when(toolA.execute(any())).thenThrow(new RuntimeException("fail"));
        when(toolB.execute(any())).thenReturn(ToolResult.ok("ok"));

        // Turn 1: tool_a fails
        ChatResponse resp1 = buildToolCallResponse("tool_a", Map.of());
        // Turn 2: tool_b succeeds
        ChatResponse resp2 = buildToolCallResponse("tool_b", Map.of());
        ChatResponse resp3 = buildStopResponse("done");
        when(llmPort.call(any())).thenReturn(resp1, resp2, resp3);

        reActLoop.run(session, llmPort, profile, List.of(toolA, toolB), "help");

        verify(toolA).execute(any());
        verify(toolB).execute(any());
    }

    @Test
    void historyComplete_allToolResultsRecorded() {
        Session session = new Session("s1", "agent1", "cli", "u1");
        Profile profile = new Profile("agent1");

        when(toolA.execute(any())).thenThrow(new RuntimeException("crash"));
        when(toolB.execute(any())).thenReturn(ToolResult.ok("success"));

        ChatResponse resp1 = buildBatchToolCallResponse(
                new String[]{"tool_a", "tool_b"},
                new Map[]{Map.of(), Map.of()}
        );
        ChatResponse resp2 = buildStopResponse("done");
        when(llmPort.call(any())).thenReturn(resp1, resp2);

        reActLoop.run(session, llmPort, profile, List.of(toolA, toolB), "help");

        List<Message> messages = session.getMessages();
        long toolMessages = messages.stream().filter(m -> "tool".equals(m.getRole())).count();
        assertThat(toolMessages).isEqualTo(2);

        boolean toolAFail = messages.stream()
                .filter(m -> "tool".equals(m.getRole()))
                .anyMatch(m -> m.getContent().contains("crash"));
        assertThat(toolAFail).isTrue();

        boolean toolBOk = messages.stream()
                .filter(m -> "tool".equals(m.getRole()))
                .anyMatch(m -> m.getContent().contains("success"));
        assertThat(toolBOk).isTrue();
    }

    @Test
    void gracefulTermination_toolFailureDoesNotPreventStop() {
        Session session = new Session("s1", "agent1", "cli", "u1");
        Profile profile = new Profile("agent1");

        when(toolA.execute(any())).thenThrow(new RuntimeException("fatal"));

        // Round 1: tool fails
        ChatResponse resp1 = buildToolCallResponse("tool_a", Map.of());
        // Round 2: model decides to stop
        ChatResponse resp2 = buildStopResponse("giving up");
        when(llmPort.call(any())).thenReturn(resp1, resp2);

        reActLoop.run(session, llmPort, profile, List.of(toolA), "help");

        // Loop should have terminated normally (not infinite)
        verify(llmPort, times(2)).call(any());
    }

    // --- Helpers ---

    private ChatResponse buildToolCallResponse(String toolName, Map<String, Object> args) {
        return buildBatchToolCallResponse(new String[]{toolName}, new Map[]{args});
    }

    private ChatResponse buildBatchToolCallResponse(String[] toolNames, Map<String, Object>[] argsList) {
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        List<AssistantMessage.ToolCall> toolCalls = new java.util.ArrayList<>();
        for (int i = 0; i < toolNames.length; i++) {
            String argsJson;
            try {
                argsJson = objectMapper.writeValueAsString(argsList[i]);
            } catch (Exception e) {
                argsJson = "{}";
            }
            toolCalls.add(new AssistantMessage.ToolCall("tc" + i, "function", toolNames[i], argsJson));
        }
        var assistantMsg = new AssistantMessage("", Map.of(), toolCalls);
        return new ChatResponse(List.of(new Generation(assistantMsg)));
    }

    private ChatResponse buildStopResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
