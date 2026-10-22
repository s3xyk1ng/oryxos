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
 * DingTalkNotifyAdapter 单元测试。
 * 使用 JDK 内置 HttpServer 模拟钉钉 webhook 服务端，验证真实 HTTP 链路。
 */
@DisplayName("钉钉通知适配器测试")
class DingTalkNotifyAdapterTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer mockServer;
    private int port;

    // 记录收到的请求，用于断言
    private final List<ReceivedRequest> receivedRequests = new ArrayList<>();

    private record ReceivedRequest(String path, String query, String body) {}

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
     * 构造适配器实例，注入自定义 HttpClient（指向 mock server）和可选 Sandbox。
     */
    private DingTalkNotifyAdapter createAdapter(Sandbox sandbox) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();
        return new DingTalkNotifyAdapter(httpClient, sandbox);
    }

    private DingTalkNotifyAdapter createAdapter() {
        return createAdapter(null);
    }

    @Test
    @DisplayName("发送成功：mock server 返回 200，payload 含 content 字段")
    void sendSuccess() throws Exception {
        // mock server 返回 200，记录收到的请求
        mockServer.createContext("/robot/send", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String bodyStr = new String(body);
            String query = exchange.getRequestURI().getQuery();
            receivedRequests.add(new ReceivedRequest(
                    exchange.getRequestURI().getPath(), query, bodyStr));

            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        mockServer.start();

        DingTalkNotifyAdapter adapter = createAdapter();
        NotifyTarget target = new NotifyTarget("dingtalk",
                Map.of("url", "http://localhost:" + port + "/robot/send?access_token=test123"));

        String result = adapter.send(target, "钉钉测试消息");

        // 断言返回成功信息
        assertTrue(result.contains("DingTalk notification sent successfully"),
                "应返回成功信息，实际: " + result);
        assertTrue(result.contains("200"), "应包含 HTTP 状态码");

        // 断言 mock server 收到了 POST 请求
        assertEquals(1, receivedRequests.size(), "应收到 1 个请求");
        assertEquals("/robot/send", receivedRequests.get(0).path());

        // 断言 payload 是 JSON 且包含 content 字段
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = JSON.readValue(receivedRequests.get(0).body(), Map.class);
        assertEquals("text", payload.get("msgtype"));
        @SuppressWarnings("unchecked")
        Map<String, String> text = (Map<String, String>) payload.get("text");
        assertEquals("钉钉测试消息", text.get("content"), "payload 中的 content 应与传入一致");
    }

    @Test
    @DisplayName("类型不匹配：type=webhook 时返回 unsupported，不发 HTTP 请求")
    void sendTypeMismatch() {
        mockServer.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            receivedRequests.add(new ReceivedRequest(
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getQuery(),
                    new String(body)));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        mockServer.start();

        DingTalkNotifyAdapter adapter = createAdapter();
        NotifyTarget target = new NotifyTarget("webhook",
                Map.of("url", "http://localhost:" + port + "/webhook"));

        String result = adapter.send(target, "hello");

        assertEquals("unsupported type: webhook", result);
        assertTrue(receivedRequests.isEmpty(), "不应发出任何 HTTP 请求");
    }

    @Test
    @DisplayName("URL 缺失：type=dingtalk 但 config 无 url，抛 IllegalArgumentException")
    void sendUrlMissing() {
        DingTalkNotifyAdapter adapter = createAdapter();
        NotifyTarget target = new NotifyTarget("dingtalk", Map.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> adapter.send(target, "hello"));
        assertTrue(ex.getMessage().contains("url"), "异常信息应提及 url");
    }

    @Test
    @DisplayName("HTTP 5xx：mock server 返回 500，抛 RuntimeException")
    void sendHttp5xx() {
        mockServer.createContext("/robot/send", exchange -> {
            String resp = "{\"errcode\":310000,\"errmsg\":\"sign not match\"}";
            byte[] respBytes = resp.getBytes();
            exchange.sendResponseHeaders(500, respBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(respBytes);
            }
        });
        mockServer.start();

        DingTalkNotifyAdapter adapter = createAdapter();
        NotifyTarget target = new NotifyTarget("dingtalk",
                Map.of("url", "http://localhost:" + port + "/robot/send?access_token=test123"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> adapter.send(target, "hello"));
        assertTrue(ex.getMessage().contains("500"),
                "异常信息应包含 HTTP 状态码，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("URL 来自 target：不同 url 的 NotifyTarget 发往不同路径")
    void sendUrlComesFromTarget() throws Exception {
        mockServer.createContext("/robot-a", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            receivedRequests.add(new ReceivedRequest(
                    "/robot-a", exchange.getRequestURI().getQuery(), new String(body)));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        mockServer.createContext("/robot-b", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            receivedRequests.add(new ReceivedRequest(
                    "/robot-b", exchange.getRequestURI().getQuery(), new String(body)));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        mockServer.start();

        DingTalkNotifyAdapter adapter = createAdapter();

        // 第一个 target 指向 /robot-a
        NotifyTarget targetA = new NotifyTarget("dingtalk",
                Map.of("url", "http://localhost:" + port + "/robot-a?access_token=aaa"));
        String resultA = adapter.send(targetA, "msg-a");
        assertTrue(resultA.contains("successfully"));

        // 第二个 target 指向 /robot-b
        NotifyTarget targetB = new NotifyTarget("dingtalk",
                Map.of("url", "http://localhost:" + port + "/robot-b?access_token=bbb"));
        String resultB = adapter.send(targetB, "msg-b");
        assertTrue(resultB.contains("successfully"));

        // 断言两次请求发往不同路径
        assertEquals(2, receivedRequests.size());
        assertEquals("/robot-a", receivedRequests.get(0).path());
        assertEquals("/robot-b", receivedRequests.get(1).path());
    }

    @Test
    @DisplayName("签名：配置 secret 时，URL 拼接 timestamp 和 sign 参数")
    void signUrlAppendsTimestampAndSign() {
        DingTalkNotifyAdapter adapter = createAdapter();

        String originalUrl = "https://oapi.dingtalk.com/robot/send?access_token=abc123";
        String secret = "SEC1234567890";

        String signedUrl = adapter.signUrl(originalUrl, secret);

        // 断言 URL 包含 timestamp 和 sign 参数
        assertTrue(signedUrl.contains("timestamp="), "签名后 URL 应包含 timestamp 参数");
        assertTrue(signedUrl.contains("sign="), "签名后 URL 应包含 sign 参数");
        assertTrue(signedUrl.startsWith(originalUrl + "&"), "签名应拼接在原始 URL 之后");
    }

    @Test
    @DisplayName("签名：secret 为空时，URL 不变")
    void signUrlNoSecretReturnsOriginal() {
        DingTalkNotifyAdapter adapter = createAdapter();

        String originalUrl = "https://oapi.dingtalk.com/robot/send?access_token=abc123";

        assertEquals(originalUrl, adapter.signUrl(originalUrl, null));
        assertEquals(originalUrl, adapter.signUrl(originalUrl, ""));
        assertEquals(originalUrl, adapter.signUrl(originalUrl, "   "));
    }

    @Test
    @DisplayName("签名：URL 已有 query 参数时，用 & 拼接而非 ?")
    void signUrlWithExistingQuery() {
        DingTalkNotifyAdapter adapter = createAdapter();

        String urlWithQuery = "https://oapi.dingtalk.com/robot/send?access_token=abc123";
        String signedUrl = adapter.signUrl(urlWithQuery, "SEC_test");

        // URL 已含 ?，签名应用 & 拼接
        assertTrue(signedUrl.contains("&timestamp="),
                "已有 query 参数时应使用 & 拼接，实际: " + signedUrl);
    }
}
