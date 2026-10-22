package com.oryxos.tool.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.model.Sandbox;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebhookNotifyAdapter 单元测试。
 * 使用 JDK 内置 HttpServer 模拟 webhook 服务端，验证真实 HTTP 链路。
 */
@DisplayName("Webhook 通知适配器测试")
class WebhookNotifyAdapterTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer mockServer;
    private int port;

    // 记录收到的请求，用于断言
    private final List<ReceivedRequest> receivedRequests = new ArrayList<>();

    private record ReceivedRequest(String path, String body) {}

    @BeforeEach
    void setUp() throws IOException {
        receivedRequests.clear();
        // 随机端口启动 mock server
        mockServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        port = mockServer.getAddress().getPort();
        mockServer.setExecutor(Executors.newSingleThreadExecutor());
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    /**
     * 构造适配器实例，注入自定义 HttpClient（指向 mock server）。
     */
    private WebhookNotifyAdapter createAdapter() {
        return createAdapter(null);
    }

    /**
     * 构造适配器实例，注入自定义 HttpClient（指向 mock server）和可选 Sandbox。
     */
    private WebhookNotifyAdapter createAdapter(Sandbox sandbox) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();
        return new WebhookNotifyAdapter(httpClient, sandbox);
    }

    @Test
    @DisplayName("发送成功：mock server 返回 200，payload 含 content 字段")
    void sendSuccess() throws Exception {
        // mock server 返回 200，记录收到的请求
        mockServer.createContext("/webhook", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String bodyStr = new String(body);
            receivedRequests.add(new ReceivedRequest(exchange.getRequestURI().getPath(), bodyStr));

            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        mockServer.start();

        WebhookNotifyAdapter adapter = createAdapter();
        NotifyTarget target = new NotifyTarget("webhook",
                Map.of("url", "http://localhost:" + port + "/webhook"));

        String result = adapter.send(target, "hello");

        // 断言返回成功信息
        assertTrue(result.contains("Notification sent successfully"),
                "应返回成功信息，实际: " + result);
        assertTrue(result.contains("200"), "应包含 HTTP 状态码");

        // 断言 mock server 收到了 POST 请求
        assertEquals(1, receivedRequests.size(), "应收到 1 个请求");
        assertEquals("/webhook", receivedRequests.get(0).path());

        // 断言 payload 是 JSON 且包含 content 字段
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = JSON.readValue(receivedRequests.get(0).body(), Map.class);
        assertEquals("text", payload.get("msgtype"));
        @SuppressWarnings("unchecked")
        Map<String, String> text = (Map<String, String>) payload.get("text");
        assertEquals("hello", text.get("content"), "payload 中的 content 应与传入一致");
    }

    @Test
    @DisplayName("类型不匹配：type=smtp 时返回 unsupported，不发 HTTP 请求")
    void sendTypeMismatch() {
        // 设置一个会记录请求的 handler
        mockServer.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            receivedRequests.add(new ReceivedRequest(exchange.getRequestURI().getPath(),
                    new String(body)));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        mockServer.start();

        WebhookNotifyAdapter adapter = createAdapter();
        NotifyTarget target = new NotifyTarget("smtp", Map.of("host", "smtp.example.com"));

        String result = adapter.send(target, "hello");

        assertEquals("unsupported type: smtp", result);
        assertTrue(receivedRequests.isEmpty(), "不应发出任何 HTTP 请求");
    }

    @Test
    @DisplayName("URL 缺失：type=webhook 但 config 无 url，抛 IllegalArgumentException")
    void sendUrlMissing() {
        WebhookNotifyAdapter adapter = createAdapter();
        NotifyTarget target = new NotifyTarget("webhook", Map.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> adapter.send(target, "hello"));
        assertTrue(ex.getMessage().contains("url"), "异常信息应提及 url");
    }

    @Test
    @DisplayName("HTTP 5xx：mock server 返回 500，抛 RuntimeException")
    void sendHttp5xx() {
        mockServer.createContext("/webhook", exchange -> {
            String resp = "{\"error\":\"internal server error\"}";
            byte[] respBytes = resp.getBytes();
            exchange.sendResponseHeaders(500, respBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(respBytes);
            }
        });
        mockServer.start();

        WebhookNotifyAdapter adapter = createAdapter();
        NotifyTarget target = new NotifyTarget("webhook",
                Map.of("url", "http://localhost:" + port + "/webhook"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> adapter.send(target, "hello"));
        assertTrue(ex.getMessage().contains("500"),
                "异常信息应包含 HTTP 状态码，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("URL 来自 target：不同 url 的 NotifyTarget 发往不同路径")
    void sendUrlComesFromTarget() throws Exception {
        mockServer.createContext("/path-a", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            receivedRequests.add(new ReceivedRequest("/path-a", new String(body)));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        mockServer.createContext("/path-b", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            receivedRequests.add(new ReceivedRequest("/path-b", new String(body)));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        mockServer.start();

        WebhookNotifyAdapter adapter = createAdapter();

        // 第一个 target 指向 /path-a
        NotifyTarget targetA = new NotifyTarget("webhook",
                Map.of("url", "http://localhost:" + port + "/path-a"));
        String resultA = adapter.send(targetA, "msg-a");
        assertTrue(resultA.contains("successfully"));

        // 第二个 target 指向 /path-b
        NotifyTarget targetB = new NotifyTarget("webhook",
                Map.of("url", "http://localhost:" + port + "/path-b"));
        String resultB = adapter.send(targetB, "msg-b");
        assertTrue(resultB.contains("successfully"));

        // 断言两次请求发往不同路径
        assertEquals(2, receivedRequests.size());
        assertEquals("/path-a", receivedRequests.get(0).path());
        assertEquals("/path-b", receivedRequests.get(1).path());
    }
}
