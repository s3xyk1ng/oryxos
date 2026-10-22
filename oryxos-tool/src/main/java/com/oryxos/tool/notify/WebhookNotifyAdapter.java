package com.oryxos.tool.notify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.model.Sandbox;
import com.oryxos.core.model.SandboxAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用 Webhook 适配器：通过 HTTP POST 发送 JSON payload。
 * 兼容企业微信/飞书/钉钉群机器人的 msgtype=text 格式。
 * 核心阶段唯一实现的适配器，扩展阶段新增专用渠道适配器。
 */
@Component
public class WebhookNotifyAdapter implements NotifyChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotifyAdapter.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient DEFAULT_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final HttpClient httpClient;
    private final Sandbox sandbox;

    /**
     * Spring 容器注入时使用默认 HttpClient。
     * Sandbox 为可选依赖，核心阶段可能尚未实现（D=9 接线）。
     */
    @Autowired
    public WebhookNotifyAdapter(@Autowired(required = false) Sandbox sandbox) {
        this.httpClient = DEFAULT_HTTP_CLIENT;
        this.sandbox = sandbox;
    }

    /**
     * 测试用：注入自定义 HttpClient 和 Sandbox。
     */
    WebhookNotifyAdapter(HttpClient httpClient, Sandbox sandbox) {
        this.httpClient = httpClient;
        this.sandbox = sandbox;
    }

    @Override
    public String send(NotifyTarget target, String content) {
        // 类型不匹配，交给下一个适配器处理
        if (!"webhook".equals(target.type())) {
            return "unsupported type: " + target.type();
        }

        String url = target.config().get("url");
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("webhook url not configured");
        }

        // Sandbox 白名单校验：校验在前，发送在后——没有过白名单，绝不允许发出请求
        if (sandbox != null) {
            sandbox.enforce(new SandboxAction(SandboxAction.ActionType.HTTP_REQUEST, url));
        }

        try {
            String payload = buildPayload(content);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return "Notification sent successfully (HTTP " + response.statusCode() + ")";
            }

            // 非 2xx：向上抛异常，不吞掉错误
            throw new RuntimeException(
                    "Webhook returned HTTP " + response.statusCode() + ": " + response.body());
        } catch (IOException e) {
            throw new RuntimeException("Webhook request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Webhook request interrupted: " + e.getMessage(), e);
        }
    }

    /**
     * 构建通用 Webhook payload，兼容企微/飞书/钉钉 msgtype=text 格式。
     * 使用 Jackson ObjectMapper 确保 JSON 转义安全。
     */
    private String buildPayload(String content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("msgtype", "text");
        payload.put("text", Map.of("content", content));
        try {
            return JSON.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to build webhook payload", e);
        }
    }
}
