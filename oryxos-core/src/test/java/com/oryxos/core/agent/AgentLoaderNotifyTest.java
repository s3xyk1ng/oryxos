package com.oryxos.core.agent;

import com.oryxos.core.context.ConfigLoader;
import com.oryxos.core.model.Profile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AgentLoader 对 default_notify_channel frontmatter 字段的解析测试。
 */
@DisplayName("AgentLoader 通知渠道解析测试")
class AgentLoaderNotifyTest {

    private AgentLoader agentLoader;

    @BeforeEach
    void setUp() {
        ProfileRegistry registry = mock(ProfileRegistry.class);
        ConfigLoader configLoader = mock(ConfigLoader.class);
        agentLoader = new AgentLoader(registry, configLoader);
    }

    @Test
    @DisplayName("frontmatter 含 default_notify_channel：解析到 Profile")
    void deriveProfileWithDefaultNotifyChannel() {
        Map<String, Object> frontmatter = new HashMap<>();
        frontmatter.put("name", "weather");
        frontmatter.put("provider", "deepseek");
        frontmatter.put("default_notify_channel", "team-lark");

        Profile profile = agentLoader.deriveProfile(frontmatter);

        assertEquals("team-lark", profile.getDefaultNotifyChannel(),
                "应正确解析 default_notify_channel 字段");
    }

    @Test
    @DisplayName("frontmatter 不含 default_notify_channel：默认 null，不报错")
    void deriveProfileWithoutDefaultNotifyChannel() {
        Map<String, Object> frontmatter = new HashMap<>();
        frontmatter.put("name", "assistant");
        frontmatter.put("provider", "deepseek");

        Profile profile = agentLoader.deriveProfile(frontmatter);

        assertNull(profile.getDefaultNotifyChannel(),
                "未配置时应为 null");
        assertEquals("assistant", profile.getName());
    }
}
