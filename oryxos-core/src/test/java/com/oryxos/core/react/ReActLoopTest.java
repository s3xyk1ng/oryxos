package com.oryxos.core.react;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oryxos.core.agent.ProviderService;
import com.oryxos.core.model.*;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

/** ReActLoop 行为验证：循环终止、消息累积、最大轮数兜底。 */
@ExtendWith(MockitoExtension.class)
class ReActLoopTest {

    @Mock private PromptBuilder promptBuilder;
    @Mock private ProviderService providerService;
    @Mock private ToolExecutor toolExecutor;

    private ReActLoop loop;

    @BeforeEach
    void setUp() {
        loop = new ReActLoop(promptBuilder, providerService, toolExecutor);
    }

    @Test
    @DisplayName("无 tool_call 直接返回：调一次 LLM，循环 1 次结束")
    void noToolCall_returnsDirectly() {
        Profile profile = new Profile("agent");
        Session session = new Session("s1", "agent", "cli", "user");

        ProviderResponse response = new ProviderResponse("Hello!", Collections.emptyList(), null);
        Prompt p1 = mockPromptWith(List.of(new UserMessage("hi")));
        when(promptBuilder.build(any(), any())).thenReturn(p1);
        when(providerService.chat(anyString(), any(), any())).thenReturn(response);

        ProviderResponse result = loop.run(session, profile, "hi");

        assertEquals("Hello!", result.content());
        verify(providerService, times(1)).chat(anyString(), any(), any());
    }

    @Test
    @DisplayName("有 tool_call 继续循环：执行工具后进入下一轮")
    void hasToolCall_continuesLoop() {
        Profile profile = new Profile("agent");
        Session session = new Session("s1", "agent", "cli", "user");

        // 第一轮返回 tool_call，第二轮返回最终响应
        ToolCall tc = new ToolCall("tc-1", "http_get", "{}");
        ProviderResponse withTool = new ProviderResponse("", List.of(tc), null);
        ProviderResponse finalResp = new ProviderResponse("Done!", Collections.emptyList(), null);

        Prompt p1 = mockPromptWith(Collections.emptyList());
        Prompt p2 = mockPromptWith(List.of(new UserMessage("get weather")));
        when(promptBuilder.build(any(), any())).thenReturn(p1, p2);
        when(providerService.chat(anyString(), any(), any())).thenReturn(withTool, finalResp);
        when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.ok("data"));

        ProviderResponse result = loop.run(session, profile, "get weather");

        assertEquals("Done!", result.content());
        verify(providerService, times(2)).chat(anyString(), any(), any());
        verify(toolExecutor, times(1)).execute(any(), any());
    }

    @Test
    @DisplayName("最大轮数强制终止：达到 maxIterations 后返回最后一次响应")
    void maxIterations_forceStops() {
        Profile profile = new Profile("agent");
        profile.setMaxIterations(3);
        Session session = new Session("s1", "agent", "cli", "user");

        // 每次都返回 tool_call
        ToolCall tc = new ToolCall("tc-1", "tool", "{}");
        ProviderResponse withTool = new ProviderResponse("thinking", List.of(tc), null);

        Prompt p1 = mockPromptWith(Collections.emptyList());
        Prompt p2 = mockPromptWith(List.of(new UserMessage("start")));
        when(promptBuilder.build(any(), any())).thenReturn(p1, p2, p2);
        when(providerService.chat(anyString(), any(), any())).thenReturn(withTool);
        when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.ok("result"));

        ProviderResponse result = loop.run(session, profile, "start");

        // 应该恰好调 3 次 LLM（maxIterations=3）
        verify(providerService, times(3)).chat(anyString(), any(), any());
        assertNotNull(result);
    }

    @Test
    @DisplayName("用户消息追加到 Session：run 后 Session 第一条是 role=user")
    void userMessage_appendedToSession() {
        Profile profile = new Profile("agent");
        Session session = new Session("s1", "agent", "cli", "user");

        Prompt p1 = mockPromptWith(List.of(new UserMessage("hello")));
        when(promptBuilder.build(any(), any())).thenReturn(p1);
        when(providerService.chat(anyString(), any(), any()))
                .thenReturn(new ProviderResponse("reply", Collections.emptyList(), null));

        loop.run(session, profile, "hello");

        assertEquals("user", session.getMessages().get(0).getRole());
        assertEquals("hello", session.getMessages().get(0).getContent());
    }

    @Test
    @DisplayName("助手消息每轮追加：3 轮循环后有 3 条 role=assistant")
    void assistantMessage_appendedEachIteration() {
        Profile profile = new Profile("agent");
        profile.setMaxIterations(3);
        Session session = new Session("s1", "agent", "cli", "user");

        ToolCall tc = new ToolCall("tc-1", "tool", "{}");
        Prompt p1 = mockPromptWith(Collections.emptyList());
        Prompt p2 = mockPromptWith(List.of(new UserMessage("go")));
        when(promptBuilder.build(any(), any())).thenReturn(p1, p2, p2);
        when(providerService.chat(anyString(), any(), any()))
                .thenReturn(new ProviderResponse("thinking", List.of(tc), null));
        when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.ok("ok"));

        loop.run(session, profile, "go");

        long assistantCount =
                session.getMessages().stream().filter(m -> "assistant".equals(m.getRole())).count();
        assertEquals(3, assistantCount);
    }

    @Test
    @DisplayName("工具结果追加到 Session：tool_call 执行后有 role=tool 消息")
    void toolResult_appendedToSession() {
        Profile profile = new Profile("agent");
        Session session = new Session("s1", "agent", "cli", "user");

        ToolCall tc = new ToolCall("tc-1", "http_get", "{}");
        Prompt p1 = mockPromptWith(Collections.emptyList());
        Prompt p2 = mockPromptWith(List.of(new UserMessage("weather")));
        when(promptBuilder.build(any(), any())).thenReturn(p1, p2);
        when(providerService.chat(anyString(), any(), any()))
                .thenReturn(new ProviderResponse("", List.of(tc), null))
                .thenReturn(new ProviderResponse("done", Collections.emptyList(), null));
        when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.ok("weather data"));

        loop.run(session, profile, "weather");

        boolean hasToolMsg =
                session.getMessages().stream().anyMatch(m -> "tool".equals(m.getRole()));
        assertTrue(hasToolMsg, "Session should have a tool message");
    }

    @Test
    @DisplayName("message 为 null 不追加：后续迭代传 null，Session 不多出 user 消息")
    void nullMessage_notAppended() {
        Profile profile = new Profile("agent");
        Session session = new Session("s1", "agent", "cli", "user");

        Prompt p1 = mockPromptWith(Collections.emptyList());
        when(promptBuilder.build(any(), any())).thenReturn(p1);
        when(providerService.chat(anyString(), any(), any()))
                .thenReturn(new ProviderResponse("reply", Collections.emptyList(), null));

        loop.run(session, profile, null);

        // null message 不追加 user 消息，但 assistant 响应仍会追加
        long userCount =
                session.getMessages().stream().filter(m -> "user".equals(m.getRole())).count();
        assertEquals(0, userCount, "No user message should be added when message is null");
    }

    @Test
    @DisplayName("回归：模型一直调工具直到强制停止，不抛异常不无限循环")
    void regression_modelKeepsCallingTools_forceStops() {
        Profile profile = new Profile("agent");
        profile.setMaxIterations(10);
        Session session = new Session("s1", "agent", "cli", "user");

        // 每次都返回 tool_call
        ToolCall tc = new ToolCall("tc-1", "tool", "{}");
        ProviderResponse withTool = new ProviderResponse("thinking", List.of(tc), null);

        Prompt p1 = mockPromptWith(Collections.emptyList());
        Prompt p2 = mockPromptWith(List.of(new UserMessage("start")));
        when(promptBuilder.build(any(), any())).thenReturn(p1, p2);
        when(providerService.chat(anyString(), any(), any())).thenReturn(withTool);
        when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.ok("result"));

        // 不应该抛异常，应该在 10 轮后强制返回
        ProviderResponse result = loop.run(session, profile, "start");

        verify(providerService, times(10)).chat(anyString(), any(), any());
        assertNotNull(result);
        assertEquals("thinking", result.content());
    }

    /** 创建 mock Spring Prompt，stub getInstructions() 返回指定消息列表。 */
    private Prompt mockPromptWith(List<org.springframework.ai.chat.messages.Message> messages) {
        Prompt m = mock(Prompt.class);
        when(m.getInstructions()).thenReturn(messages);
        return m;
    }
}
