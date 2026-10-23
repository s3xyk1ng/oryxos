package com.oryxos.tool.notify;

import com.oryxos.core.context.ProfileContext;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.ToolResult;
import com.oryxos.storage.entity.NotifyChannelEntity;
import com.oryxos.storage.repository.NotifyChannelRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * NotifyTool 独立单元测试（6 用例）。
 * mock Repository + mock Adapter 验证渠道查找、适配器路由、参数校验、默认渠道、条件注册。
 */
@DisplayName("NotifyTool 测试")
class NotifyToolTest {

    private NotifyChannelRepository repo;
    private NotifyChannelAdapter webhookAdapter;
    private NotifyTool notifyTool;

    @BeforeEach
    void setUp() {
        repo = mock(NotifyChannelRepository.class);
        webhookAdapter = mock(NotifyChannelAdapter.class);
        notifyTool = new NotifyTool(repo, List.of(webhookAdapter));
    }

    @AfterEach
    void tearDown() {
        ProfileContext.clear();
    }

    private NotifyChannelEntity makeEntity(String name, String type, String url) {
        NotifyChannelEntity entity = new NotifyChannelEntity();
        entity.setName(name);
        entity.setType(type);
        entity.setUrl(url);
        return entity;
    }

    @Test
    @DisplayName("渠道查找：查到渠道 → 适配器返回成功 → ToolResult.ok")
    void executeChannelFound() {
        NotifyChannelEntity entity = makeEntity("team-lark", "webhook",
                "https://hook.example.com/msg");
        when(repo.findByName("team-lark")).thenReturn(Optional.of(entity));
        when(webhookAdapter.send(any(NotifyTarget.class), eq("hello")))
                .thenReturn("Notification sent successfully (HTTP 200)");

        ToolResult result = notifyTool.execute(Map.of("content", "hello", "channel", "team-lark"));

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("successfully"));
    }

    @Test
    @DisplayName("渠道不存在：返回 ToolResult.fail 包含 Channel not found")
    void executeChannelNotFound() {
        when(repo.findByName("nonexistent")).thenReturn(Optional.empty());

        ToolResult result = notifyTool.execute(Map.of("content", "hello", "channel", "nonexistent"));

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Channel not found"),
                "错误信息应包含 'Channel not found'，实际: " + result.getError());
    }

    @Test
    @DisplayName("默认渠道 fallback：LLM 不传 channel，从 ProfileContext 取默认值")
    void executeDefaultChannelFallback() {
        Profile profile = new Profile();
        profile.setDefaultNotifyChannel("team-lark");
        ProfileContext.set(profile);

        NotifyChannelEntity entity = makeEntity("team-lark", "webhook",
                "https://hook.example.com/msg");
        when(repo.findByName("team-lark")).thenReturn(Optional.of(entity));
        when(webhookAdapter.send(any(NotifyTarget.class), eq("hello")))
                .thenReturn("Notification sent successfully (HTTP 200)");

        ToolResult result = notifyTool.execute(Map.of("content", "hello"));

        assertTrue(result.isSuccess(), "应使用默认渠道成功发送");
        verify(repo).findByName("team-lark");
    }

    @Test
    @DisplayName("条件注册：渠道表有数据时 isAvailable() 返回 true")
    void isAvailableTrueWhenChannelsExist() {
        when(repo.count()).thenReturn(2L);

        assertTrue(notifyTool.isAvailable());
    }

    @Test
    @DisplayName("条件注册：渠道表为空时 isAvailable() 返回 false")
    void isAvailableFalseWhenNoChannels() {
        when(repo.count()).thenReturn(0L);

        assertFalse(notifyTool.isAvailable());
    }

    @Test
    @DisplayName("参数校验：content 为空时返回 ToolResult.fail")
    void executeContentBlank() {
        ToolResult result = notifyTool.execute(Map.of("content", "", "channel", "team-lark"));

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("content parameter is required"),
                "错误信息应包含 'content parameter is required'，实际: " + result.getError());
    }
}
