package com.oryxos.core.react;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oryxos.core.agent.ToolRegistry;
import com.oryxos.core.context.ContextLoader;
import com.oryxos.core.model.Message;
import com.oryxos.core.model.OryxTool;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;

/** PromptBuilder 行为验证：四段拼接、时间戳、截断、Memory、Tool schema、传统 switch。 */
@ExtendWith(MockitoExtension.class)
class PromptBuilderTest {

    @Mock private ContextLoader contextLoader;
    @Mock private MemoryPort memoryPort;
    @Mock private ToolRegistry toolRegistry;

    private PromptBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new PromptBuilder(contextLoader, memoryPort, toolRegistry);
    }

    @Test
    @DisplayName("System Prompt 四段拼接：包含 bootstrap + agentBody + skills + 时间")
    void systemPromptContainsAllParts() {
        when(contextLoader.loadBootstrap()).thenReturn("bootstrap content");
        when(contextLoader.loadAgentBody("agent")).thenReturn("agent body");
        when(contextLoader.loadSkills("agent")).thenReturn(Collections.emptyList());
        when(toolRegistry.getToolsForProfile(any())).thenReturn(Collections.emptyList());

        Profile profile = new Profile("agent");
        Session session = new Session("s1", "agent", "cli", "user");
        session.addMessage(new Message("user", "hello"));

        Prompt prompt = builder.build(profile, session);

        String sysContent = getSystemPromptContent(prompt);
        assertTrue(sysContent.contains("bootstrap content"), "should contain bootstrap");
        assertTrue(sysContent.contains("agent body"), "should contain agent body");
        assertTrue(sysContent.contains("Current time:"), "should contain timestamp");
    }

    @Test
    @DisplayName("当前日期时间在末尾：system prompt 末尾包含 Current time: + RFC_1123")
    void currentTimeAtEnd_inRfc1123Format() {
        when(contextLoader.loadBootstrap()).thenReturn("");
        when(contextLoader.loadAgentBody("agent")).thenReturn("body");
        when(contextLoader.loadSkills("agent")).thenReturn(Collections.emptyList());
        when(toolRegistry.getToolsForProfile(any())).thenReturn(Collections.emptyList());

        Profile profile = new Profile("agent");
        Session session = new Session("s1", "agent", "cli", "user");

        Prompt prompt = builder.build(profile, session);
        String sysContent = getSystemPromptContent(prompt);

        assertTrue(sysContent.contains("Current time:"), "should have Current time label");
        // RFC_1123 包含 "GMT" 或时区缩写
        assertTrue(sysContent.matches("(?s).*Current time:.*\\d{4}.*"), "should have date format");
    }

    @Test
    @DisplayName("对话历史截断：50 条消息 + maxHistoryTurns=20 → 只取最近 40 条")
    void historyTruncation_keepsLast40Messages() {
        when(contextLoader.loadBootstrap()).thenReturn("");
        when(contextLoader.loadAgentBody("agent")).thenReturn("");
        when(contextLoader.loadSkills("agent")).thenReturn(Collections.emptyList());
        when(toolRegistry.getToolsForProfile(any())).thenReturn(Collections.emptyList());

        Profile profile = new Profile("agent");
        profile.setMaxHistoryTurns(20); // 20 turns = 40 messages
        Session session = new Session("s1", "agent", "cli", "user");

        // 添加 50 条消息
        for (int i = 0; i < 50; i++) {
            session.addMessage(new Message("user", "msg-" + i));
        }

        Prompt prompt = builder.build(profile, session);

        // Prompt 中的消息数 = system prompt (1) + 截断后的历史 (40) = 41
        assertEquals(41, prompt.getInstructions().size());
    }

    @Test
    @DisplayName("Memory 注入：MemoryPort 返回内容 → 拼入 system prompt")
    void memoryInjection_appearsInSystemPrompt() {
        when(contextLoader.loadBootstrap()).thenReturn("");
        when(contextLoader.loadAgentBody("agent")).thenReturn("");
        when(contextLoader.loadSkills("agent")).thenReturn(Collections.emptyList());
        when(memoryPort.getMemoryContext()).thenReturn("Remember: user likes Java");
        when(toolRegistry.getToolsForProfile(any())).thenReturn(Collections.emptyList());

        Profile profile = new Profile("agent");
        Session session = new Session("s1", "agent", "cli", "user");

        Prompt prompt = builder.build(profile, session);
        String sysContent = getSystemPromptContent(prompt);

        assertTrue(sysContent.contains("Remember: user likes Java"));
    }

    @Test
    @DisplayName("Memory 为空不注入：MemoryPort 返回 null → system prompt 不含 memory 段")
    void memoryNull_notInjected() {
        when(contextLoader.loadBootstrap()).thenReturn("base");
        when(contextLoader.loadAgentBody("agent")).thenReturn("");
        when(contextLoader.loadSkills("agent")).thenReturn(Collections.emptyList());
        when(memoryPort.getMemoryContext()).thenReturn(null);
        when(toolRegistry.getToolsForProfile(any())).thenReturn(Collections.emptyList());

        Profile profile = new Profile("agent");
        Session session = new Session("s1", "agent", "cli", "user");

        Prompt prompt = builder.build(profile, session);
        String sysContent = getSystemPromptContent(prompt);

        // 确认 system prompt 包含 base 和时间戳，但不包含任何 memory 内容
        assertTrue(sysContent.contains("base"), "should contain bootstrap");
        assertTrue(sysContent.contains("Current time:"), "should contain timestamp");
        assertFalse(sysContent.contains("Remember"), "should not contain memory");
    }

    @Test
    @DisplayName("MemoryPort 不存在：memoryPort=null → 不报错，跳过 memory 段")
    void memoryPortNull_noError() {
        PromptBuilder noMemBuilder = new PromptBuilder(contextLoader, null, toolRegistry);
        when(contextLoader.loadBootstrap()).thenReturn("base");
        when(contextLoader.loadAgentBody("agent")).thenReturn("");
        when(contextLoader.loadSkills("agent")).thenReturn(Collections.emptyList());
        when(toolRegistry.getToolsForProfile(any())).thenReturn(Collections.emptyList());

        Profile profile = new Profile("agent");
        Session session = new Session("s1", "agent", "cli", "user");

        Prompt prompt = noMemBuilder.build(profile, session);
        assertNotNull(prompt);
    }

    @Test
    @DisplayName("Tool schema 生成：有 tools → system prompt 包含工具定义")
    void toolSchemaGenerated_appearsInPrompt() {
        when(contextLoader.loadBootstrap()).thenReturn("");
        when(contextLoader.loadAgentBody("agent")).thenReturn("");
        when(contextLoader.loadSkills("agent")).thenReturn(Collections.emptyList());

        OryxTool tool = mock(OryxTool.class);
        when(tool.getName()).thenReturn("http_get");
        when(tool.getDescription()).thenReturn("Fetch a URL");
        when(tool.getInputSchema()).thenReturn(Map.of("type", "object"));
        when(toolRegistry.getToolsForProfile(any())).thenReturn(List.of(tool));

        Profile profile = new Profile("agent");
        Session session = new Session("s1", "agent", "cli", "user");

        Prompt prompt = builder.build(profile, session);
        String sysContent = getSystemPromptContent(prompt);

        assertTrue(sysContent.contains("http_get"), "should contain tool name");
        assertTrue(sysContent.contains("Fetch a URL"), "should contain tool description");
    }

    @Test
    @DisplayName("无 tools：tools 为空 → system prompt 不含工具段")
    void noTools_noToolSection() {
        when(contextLoader.loadBootstrap()).thenReturn("");
        when(contextLoader.loadAgentBody("agent")).thenReturn("");
        when(contextLoader.loadSkills("agent")).thenReturn(Collections.emptyList());
        when(toolRegistry.getToolsForProfile(any())).thenReturn(Collections.emptyList());

        Profile profile = new Profile("agent");
        Session session = new Session("s1", "agent", "cli", "user");

        Prompt prompt = builder.build(profile, session);
        String sysContent = getSystemPromptContent(prompt);

        assertFalse(sysContent.contains("Available Tools:"), "should not have tools section");
    }

    @Test
    @DisplayName("传统 switch-case：tool 消息正确映射到 Prompt 中")
    void traditionalSwitch_toolMessageMapped() {
        when(contextLoader.loadBootstrap()).thenReturn("");
        when(contextLoader.loadAgentBody("agent")).thenReturn("");
        when(contextLoader.loadSkills("agent")).thenReturn(Collections.emptyList());
        when(toolRegistry.getToolsForProfile(any())).thenReturn(Collections.emptyList());

        Profile profile = new Profile("agent");
        Session session = new Session("s1", "agent", "cli", "user");
        session.addMessage(new Message("user", "hi"));
        session.addMessage(new Message("tool", "[http_get] data"));

        Prompt prompt = builder.build(profile, session);

        // 验证 tool 消息被正确映射（不应抛异常，且消息数正确）
        // system(1) + user(1) + tool(1) = 3
        assertEquals(3, prompt.getInstructions().size());
    }

    /** 提取 Prompt 中 SystemMessage 的内容。 */
    private String getSystemPromptContent(Prompt prompt) {
        for (var m : prompt.getInstructions()) {
            if (m instanceof SystemMessage sm) {
                return sm.getText();
            }
        }
        return "";
    }
}
