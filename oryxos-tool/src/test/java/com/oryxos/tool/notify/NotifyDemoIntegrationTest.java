package com.oryxos.tool.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.agent.ToolRegistry;
import com.oryxos.core.context.ProfileContext;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Sandbox;
import com.oryxos.core.model.ToolResult;
import com.oryxos.storage.entity.NotifyChannelEntity;
import com.oryxos.storage.repository.NotifyChannelRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 端到端 Demo 集成测试：模拟完整链路——
 * "用户：把'测试消息'推送一下" → Agent 调用 notify(content="测试消息") → webhook 收到消息。
 *
 * 使用真实 HttpServer + 真实 WebhookNotifyAdapter + 真实 NotifyTools 编排，
 * 仅 mock 存储层（Repository）和安全层（Sandbox）。
 */
@DisplayName("Notify 端到端 Demo 测试")
class NotifyDemoIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer webhookServer;
    private int webhookPort;
    private String receivedBody;
    private String receivedPath;

    private NotifyChannelRepository repo;
    private ToolRegistry toolRegistry;
    private NotifyTools notifyTools;

    @BeforeEach
    void setUp() throws IOException {
        // 启动 mock webhook 服务端
        receivedBody = null;
        receivedPath = null;
        webhookServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        webhookPort = webhookServer.getAddress().getPort();
        webhookServer.createContext("/webhook", exchange -> {
            receivedBody = new String(exchange.getRequestBody().readAllBytes());
            receivedPath = exchange.getRequestURI().getPath();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        webhookServer.setExecutor(Executors.newSingleThreadExecutor());
        webhookServer.start();

        // mock 存储层
        repo = mock(NotifyChannelRepository.class);
        toolRegistry = mock(ToolRegistry.class);
    }

    @AfterEach
    void tearDown() {
        ProfileContext.clear();
        if (webhookServer != null) {
            webhookServer.stop(0);
        }
    }

    /**
     * 构造真实 WebhookNotifyAdapter（指向 mock server 的 HttpClient）。
     */
    private WebhookNotifyAdapter createRealAdapter(Sandbox sandbox) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();
        return new WebhookNotifyAdapter(httpClient, sandbox);
    }

    private NotifyChannelEntity makeChannel(String name, String type, String url) {
        NotifyChannelEntity entity = new NotifyChannelEntity();
        entity.setName(name);
        entity.setType(type);
        entity.setUrl(url);
        return entity;
    }

    @Test
    @DisplayName("Demo：用户说'把测试消息推送一下' → Agent 调 notify → webhook 收到消息")
    void demoEndToEnd() throws Exception {
        // Given：注册了一个名为 "team-lark" 的 webhook 渠道
        String webhookUrl = "http://localhost:" + webhookPort + "/webhook";
        NotifyChannelEntity channel = makeChannel("team-lark", "webhook", webhookUrl);
        when(repo.count()).thenReturn(1L);
        when(repo.findByName("team-lark")).thenReturn(Optional.of(channel));

        // Given：Profile 配置了默认通知渠道
        Profile profile = new Profile();
        profile.setDefaultNotifyChannel("team-lark");
        ProfileContext.set(profile);

        // Given：Sandbox 放行（mock 不做任何校验）
        Sandbox sandbox = mock(Sandbox.class);

        // 构建真实 NotifyTools + 真实 WebhookNotifyAdapter
        WebhookNotifyAdapter adapter = createRealAdapter(sandbox);
        notifyTools = new NotifyTools(repo, List.of(adapter), toolRegistry);
        notifyTools.init();

        // When：Agent 调用 notify（LLM 只传 content，channel 走默认值）
        Map<String, Object> input = Map.of("content", "测试消息");
        ToolResult result = notifyTools.getNotifyTool().execute(input);

        // Then：发送成功
        assertTrue(result.isSuccess(), "notify 应返回成功，实际: " + result.getError());
        assertTrue(result.getContent().contains("successfully"));

        // Then：mock webhook 服务端收到了 POST 请求
        assertNotNull(receivedBody, "webhook 应收到请求");
        assertEquals("/webhook", receivedPath);

        // Then：payload 格式正确（msgtype=text, text.content=测试消息）
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = JSON.readValue(receivedBody, Map.class);
        assertEquals("text", payload.get("msgtype"));
        @SuppressWarnings("unchecked")
        Map<String, String> text = (Map<String, String>) payload.get("text");
        assertEquals("测试消息", text.get("content"));

        // Then：Sandbox 被调用了（校验在前）
        verify(sandbox).enforce(argThat(action ->
                action.getTarget().equals(webhookUrl)));
    }

    @Test
    @DisplayName("Demo：渠道未配置 → 明确报错，不静默失败")
    void demoChannelNotConfigured() {
        when(repo.count()).thenReturn(1L);
        when(repo.findByName("nonexistent")).thenReturn(Optional.empty());

        ProfileContext.set(new Profile());

        WebhookNotifyAdapter adapter = createRealAdapter(null);
        notifyTools = new NotifyTools(repo, List.of(adapter), toolRegistry);
        notifyTools.init();

        Map<String, Object> input = Map.of("content", "测试消息", "channel", "nonexistent");
        ToolResult result = notifyTools.getNotifyTool().execute(input);

        assertFalse(result.isSuccess(), "渠道不存在应返回失败");
        assertTrue(result.getError().contains("Channel not found"),
                "错误信息应明确告知渠道未找到: " + result.getError());
    }

    @Test
    @DisplayName("Demo：渠道表为空 → 工具不注册，LLM 看不到")
    void demoNoChannelsNoTool() {
        when(repo.count()).thenReturn(0L);

        WebhookNotifyAdapter adapter = createRealAdapter(null);
        notifyTools = new NotifyTools(repo, List.of(adapter), toolRegistry);
        notifyTools.init();

        assertNull(notifyTools.getNotifyTool(),
                "渠道表为空时 NotifyTool 应为 null（不注册）");
    }

    @Test
    @DisplayName("Demo：指定 channel 参数发送")
    void demoWithExplicitChannel() throws Exception {
        String webhookUrl = "http://localhost:" + webhookPort + "/webhook";
        NotifyChannelEntity channel = makeChannel("dev-group", "webhook", webhookUrl);
        when(repo.count()).thenReturn(1L);
        when(repo.findByName("dev-group")).thenReturn(Optional.of(channel));

        ProfileContext.set(new Profile());

        WebhookNotifyAdapter adapter = createRealAdapter(null);
        notifyTools = new NotifyTools(repo, List.of(adapter), toolRegistry);
        notifyTools.init();

        // LLM 明确指定 channel
        Map<String, Object> input = Map.of("content", "部署完成通知", "channel", "dev-group");
        ToolResult result = notifyTools.getNotifyTool().execute(input);

        assertTrue(result.isSuccess());
        assertNotNull(receivedBody);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = JSON.readValue(receivedBody, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, String> text = (Map<String, String>) payload.get("text");
        assertEquals("部署完成通知", text.get("content"));
    }
}
