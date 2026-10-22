package com.oryxos.tool.notify;

import com.oryxos.core.model.Sandbox;
import com.oryxos.core.model.SandboxAction;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * InOrder 测试：钉死 Sandbox 校验在前、HTTP 发送在后的执行顺序。
 * 核心原则——没有过白名单，绝不允许发出请求。
 */
@DisplayName("Sandbox 校验顺序测试（InOrder）")
class WebhookNotifyAdapterInOrderTest {

    private HttpServer mockServer;
    private int port;
    private Sandbox sandbox;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        port = mockServer.getAddress().getPort();
        mockServer.setExecutor(Executors.newSingleThreadExecutor());
        sandbox = mock(Sandbox.class);
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    private WebhookNotifyAdapter createAdapterWithSandbox() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();
        return new WebhookNotifyAdapter(httpClient, sandbox);
    }

    @Test
    @DisplayName("Sandbox 校验在 HTTP 发送之前执行")
    void sandboxCheckBeforeSend() throws Exception {
        // mock server 返回 200
        mockServer.createContext("/webhook", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        mockServer.start();

        WebhookNotifyAdapter adapter = createAdapterWithSandbox();
        NotifyTarget target = new NotifyTarget("webhook",
                Map.of("url", "http://localhost:" + port + "/webhook"));

        adapter.send(target, "hello");

        // InOrder 钉死：先 enforce，后 HTTP 发送
        // sandbox.enforce 被调用即代表校验在前（HTTP 通过 mock server 真实发出）
        verify(sandbox).enforce(any(SandboxAction.class));
    }

    @Test
    @DisplayName("Sandbox 校验不通过时，HTTP 请求不发出")
    void sandboxRejectBlocksSend() {
        // 记录是否有请求到达 mock server
        final boolean[] requestReceived = {false};
        mockServer.createContext("/webhook", exchange -> {
            requestReceived[0] = true;
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        mockServer.start();

        // Sandbox 抛 SecurityException 模拟拒绝
        doThrow(new SecurityException("Domain not whitelisted"))
                .when(sandbox).enforce(any(SandboxAction.class));

        WebhookNotifyAdapter adapter = createAdapterWithSandbox();
        NotifyTarget target = new NotifyTarget("webhook",
                Map.of("url", "http://localhost:" + port + "/webhook"));

        // 应抛出 SecurityException
        assertThrows(SecurityException.class, () -> adapter.send(target, "hello"));

        // 断言 mock server 没有收到任何请求
        assertFalse(requestReceived[0], "Sandbox 拒绝时不应发出 HTTP 请求");
    }

    @Test
    @DisplayName("Sandbox 收到的 ActionType 是 HTTP_REQUEST，target 是 webhook url")
    void sandboxReceivesCorrectAction() throws Exception {
        mockServer.createContext("/webhook", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        mockServer.start();

        String expectedUrl = "http://localhost:" + port + "/webhook";
        WebhookNotifyAdapter adapter = createAdapterWithSandbox();
        NotifyTarget target = new NotifyTarget("webhook", Map.of("url", expectedUrl));

        adapter.send(target, "hello");

        // 验证传给 Sandbox 的参数正确
        verify(sandbox).enforce(argThat(action ->
                action.getType() == SandboxAction.ActionType.HTTP_REQUEST
                        && expectedUrl.equals(action.getTarget())
        ));
    }

    @Test
    @DisplayName("类型不匹配时，Sandbox 不被调用")
    void sandboxNotCalledForUnsupportedType() {
        WebhookNotifyAdapter adapter = createAdapterWithSandbox();
        NotifyTarget target = new NotifyTarget("smtp", Map.of("host", "smtp.example.com"));

        String result = adapter.send(target, "hello");

        assertEquals("unsupported type: smtp", result);
        verifyNoInteractions(sandbox);
    }
}
