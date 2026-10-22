package com.oryxos.cli.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.oryxos.core.agent.AgentLoader;
import com.oryxos.core.agent.AgentService;
import com.oryxos.core.agent.ProfileRegistry;
import com.oryxos.core.agent.SessionManager;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ChatCommand 单元测试（重命令，Mock Spring Bean）。 覆盖 4 个验收点：/quit 退出、空输入跳过、异常处理、正常调用。 */
@DisplayName("ChatCommand 测试")
class ChatCommandTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    private AgentService agentService;
    private SessionManager sessionManager;
    private ProfileRegistry profileRegistry;
    private AgentLoader agentLoader;

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));

        agentService = mock(AgentService.class);
        sessionManager = mock(SessionManager.class);
        profileRegistry = mock(ProfileRegistry.class);
        agentLoader = mock(AgentLoader.class);
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    @DisplayName("/quit 退出对话循环")
    void quitExitsChatLoop() {
        // Given: 输入 /quit
        String input = "/quit\n";
        System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));

        Profile profile = new Profile("weather");
        profile.setDescription("Weather Agent");
        profile.setProvider("deepseek");
        profile.setModel("deepseek-chat");
        when(profileRegistry.size()).thenReturn(1);
        when(profileRegistry.getAll()).thenReturn(java.util.List.of(profile));
        when(profileRegistry.getByName("weather")).thenReturn(profile);
        when(sessionManager.getOrCreateSession("cli", "cli-user", "weather"))
                .thenReturn(new Session("test-id", "weather", "cli", "cli-user"));

        ChatCommand cmd =
                new ChatCommand(agentService, sessionManager, profileRegistry, agentLoader);

        // When: 执行 chat
        cmd.run();

        // Then: 输出包含 Bye
        String output = outContent.toString();
        assertTrue(output.contains("Bye"));
    }

    @Test
    @DisplayName("空输入跳过，不触发 Agent 调用")
    void emptyInputSkipped() {
        // Given: 输入空行 + /quit
        String input = "\n/quit\n";
        System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));

        Profile profile = new Profile("weather");
        profile.setDescription("Weather Agent");
        profile.setProvider("deepseek");
        profile.setModel("deepseek-chat");
        when(profileRegistry.size()).thenReturn(1);
        when(profileRegistry.getAll()).thenReturn(java.util.List.of(profile));
        when(profileRegistry.getByName("weather")).thenReturn(profile);
        when(sessionManager.getOrCreateSession("cli", "cli-user", "weather"))
                .thenReturn(new Session("test-id", "weather", "cli", "cli-user"));

        ChatCommand cmd =
                new ChatCommand(agentService, sessionManager, profileRegistry, agentLoader);
        cmd.run();

        // Then: agentService.process 未被调用
        verify(agentService, never()).process(any(), anyString());
    }

    @Test
    @DisplayName("异常时打印 Error 信息，不抛堆栈")
    void exceptionPrintsErrorMessage() {
        // Given: AgentService 抛异常
        String input = "hello\n/quit\n";
        System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));

        Profile profile = new Profile("weather");
        profile.setDescription("Weather Agent");
        profile.setProvider("deepseek");
        profile.setModel("deepseek-chat");
        Session session = new Session("test-id", "weather", "cli", "cli-user");
        when(profileRegistry.size()).thenReturn(1);
        when(profileRegistry.getAll()).thenReturn(java.util.List.of(profile));
        when(profileRegistry.getByName("weather")).thenReturn(profile);
        when(sessionManager.getOrCreateSession("cli", "cli-user", "weather")).thenReturn(session);
        when(agentService.process(session, "hello"))
                .thenThrow(new RuntimeException("API key invalid"));

        ChatCommand cmd =
                new ChatCommand(agentService, sessionManager, profileRegistry, agentLoader);
        cmd.run();

        // Then: 输出包含 Error
        String errOutput = errContent.toString();
        assertTrue(errOutput.contains("Error"));
        assertTrue(errOutput.contains("API key invalid"));
    }

    @Test
    @DisplayName("正常调用 AgentService.process 并打印结果")
    void normalCallProcessesAndPrintsResult() {
        // Given: AgentService 正常返回
        String input = "hello\n/quit\n";
        System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));

        Profile profile = new Profile("weather");
        profile.setDescription("Weather Agent");
        profile.setProvider("deepseek");
        profile.setModel("deepseek-chat");
        Session session = new Session("test-id", "weather", "cli", "cli-user");
        when(profileRegistry.size()).thenReturn(1);
        when(profileRegistry.getAll()).thenReturn(java.util.List.of(profile));
        when(profileRegistry.getByName("weather")).thenReturn(profile);
        when(sessionManager.getOrCreateSession("cli", "cli-user", "weather")).thenReturn(session);
        when(agentService.process(session, "hello")).thenReturn("The weather is sunny.");

        ChatCommand cmd =
                new ChatCommand(agentService, sessionManager, profileRegistry, agentLoader);
        cmd.run();

        // Then: 输出包含 Agent 响应
        String output = outContent.toString();
        assertTrue(output.contains("The weather is sunny."));
    }
}
