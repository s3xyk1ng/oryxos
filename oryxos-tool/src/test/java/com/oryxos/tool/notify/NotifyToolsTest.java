package com.oryxos.tool.notify;

import com.oryxos.core.agent.ToolRegistry;
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
 * NotifyTools 编排逻辑单元测试。
 * mock Repository + mock Adapter 验证渠道查找、适配器路由、参数校验、默认渠道等。
 */
@DisplayName("NotifyTools 编排测试")
class NotifyToolsTest {

    private NotifyChannelRepository repo;
    private NotifyChannelAdapter webhookAdapter;
    private ToolRegistry toolRegistry;
    private NotifyTools notifyTools;

    @BeforeEach
    void setUp() {
        repo = mock(NotifyChannelRepository.class);
        webhookAdapter = mock(NotifyChannelAdapter.class);
        toolRegistry = mock(ToolRegistry.class);

        // mock 渠道表有数据，让 @PostConstruct 创建 NotifyTool
        when(repo.count()).thenReturn(1L);

        notifyTools = new NotifyTools(repo, List.of(webhookAdapter), toolRegistry);
        // 手动触发 @PostConstruct
        notifyTools.init();
    }

    @AfterEach
    void tearDown() {
        ProfileContext.clear();
    }

    private NotifyTools.NotifyTool tool() {
        return notifyTools.getNotifyTool();
    }

    private NotifyChannelEntity makeEntity(String name, String type, String url) {
        NotifyChannelEntity entity = new NotifyChannelEntity();
        entity.setName(name);
        entity.setType(type);
        entity.setUrl(url);
        return entity;
    }

    @Test
    @DisplayName("渠道不存在：返回 ToolResult.fail 包含 Channel not found")
    void executeChannelNotFound() {
        when(repo.findByName("nonexistent")).thenReturn(Optional.empty());

        ToolResult result = tool().execute(Map.of("content", "hello", "channel", "nonexistent"));

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Channel not found"),
                "错误信息应包含 'Channel not found'，实际: " + result.getError());
    }

    @Test
    @DisplayName("正常发送：查到渠道 → 适配器返回成功 → ToolResult.ok")
    void executeSuccess() {
        NotifyChannelEntity entity = makeEntity("team-lark", "webhook",
                "https://hook.example.com/msg");
        when(repo.findByName("team-lark")).thenReturn(Optional.of(entity));
        when(webhookAdapter.send(any(NotifyTarget.class), eq("hello")))
                .thenReturn("Notification sent successfully (HTTP 200)");

        ToolResult result = tool().execute(Map.of("content", "hello", "channel", "team-lark"));

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("successfully"),
                "成功信息应包含 'successfully'，实际: " + result.getContent());
    }

    @Test
    @DisplayName("无匹配适配器：所有适配器返回 unsupported → ToolResult.fail")
    void executeNoMatchingAdapter() {
        NotifyChannelEntity entity = makeEntity("mail-smtp", "smtp", "smtp://mail.example.com");
        when(repo.findByName("mail-smtp")).thenReturn(Optional.of(entity));
        // webhook 适配器对 smtp 类型返回 unsupported
        when(webhookAdapter.send(any(NotifyTarget.class), anyString()))
                .thenReturn("unsupported type: smtp");

        ToolResult result = tool().execute(Map.of("content", "hello", "channel", "mail-smtp"));

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("No adapter supports"),
                "错误信息应包含 'No adapter supports'，实际: " + result.getError());
    }

    @Test
    @DisplayName("content 为空：返回 ToolResult.fail 包含 content parameter is required")
    void executeContentBlank() {
        ToolResult result = tool().execute(Map.of("content", "", "channel", "team-lark"));

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("content parameter is required"),
                "错误信息应包含 'content parameter is required'，实际: " + result.getError());
    }

    @Test
    @DisplayName("默认渠道 fallback：LLM 不传 channel，从 ProfileContext 取默认值")
    void executeDefaultChannelFallback() {
        // 设置 ProfileContext 中的默认渠道
        Profile profile = new Profile();
        profile.setDefaultNotifyChannel("team-lark");
        ProfileContext.set(profile);

        NotifyChannelEntity entity = makeEntity("team-lark", "webhook",
                "https://hook.example.com/msg");
        when(repo.findByName("team-lark")).thenReturn(Optional.of(entity));
        when(webhookAdapter.send(any(NotifyTarget.class), eq("hello")))
                .thenReturn("Notification sent successfully (HTTP 200)");

        // 不传 channel 参数
        ToolResult result = tool().execute(Map.of("content", "hello"));

        assertTrue(result.isSuccess(), "应使用默认渠道成功发送");
        // 验证确实是用 "team-lark" 查询的
        verify(repo).findByName("team-lark");
    }
}
