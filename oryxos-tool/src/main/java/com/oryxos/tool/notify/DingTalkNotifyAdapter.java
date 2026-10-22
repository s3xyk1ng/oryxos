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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 钉钉群机器人适配器：通过 HTTP POST 发送 JSON payload。
 * 钉钉 webhook 格式与通用 webhook 一致（msgtype=text），额外支持 secret 签名校验。
 * 签名算法：HMAC-SHA256，timestamp + "\n" + secret → Base64 → URL encode → 拼接到 URL。
 */
@Component
public class DingTalkNotifyAdapter implements NotifyChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(DingTalkNotifyAdapter.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient DEFAULT_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final HttpClient httpClient;
    private final Sandbox sandbox;

    /**
     * Spring 容器注入时使用默认 HttpClient。
     */
    @Autowired
    public DingTalkNotifyAdapter(@Autowired(required = false) Sandbox sandbox) {
        this.httpClient = DEFAULT_HTTP_CLIENT;
        this.sandbox = sandbox;
    }

    /**
     * 测试用：注入自定义 HttpClient 和 Sandbox。
     */
    DingTalkNotifyAdapter(HttpClient httpClient, Sandbox sandbox) {
        this.httpClient = httpClient;
        this.sandbox = sandbox;
    }

    @Override
    public String send(NotifyTarget target, String content) {
        // 类型不匹配，交给下一个适配器处理
        if (!"dingtalk".equals(target.type())) {
            return "unsupported type: " + target.type();
        }

        String url = target.config().get("url");
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("dingtalk webhook url not configured");
        }

        // Sandbox 白名单校验：校验在前，发送在后——没有过白名单，绝不允许发出请求
        if (sandbox != null) {
            sandbox.enforce(new SandboxAction(SandboxAction.ActionType.HTTP_REQUEST, url));
        }

        try {
            // 如果配置了 secret，计算签名并拼接到 URL
            String signedUrl = signUrl(url, target.config().get("secret"));
            String payload = buildPayload(content);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(signedUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return "DingTalk notification sent successfully (HTTP " + response.statusCode() + ")";
            }

            // 非 2xx：向上抛异常，不吞掉错误
            throw new RuntimeException(
                    "DingTalk webhook returned HTTP " + response.statusCode() + ": " + response.body());
        } catch (IOException e) {
            throw new RuntimeException("DingTalk webhook request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("DingTalk webhook request interrupted: " + e.getMessage(), e);
        }
    }

    /**
     * 钉钉签名算法：HMAC-SHA256。
     * 如果 secret 为空则直接返回原始 URL。
     * 签名步骤：timestamp + "\n" + secret → HMAC-SHA256 → Base64 → URL encode → 拼接到 URL。
     */
    String signUrl(String url, String secret) {
        if (secret == null || secret.isBlank()) {
            return url;
        }

        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign = URLEncoder.encode(
                    java.util.Base64.getEncoder().encodeToString(signData),
                    StandardCharsets.UTF_8);

            // 拼接 timestamp 和 sign 到 URL
            String separator = url.contains("?") ? "&" : "?";
            return url + separator + "timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign DingTalk webhook URL: " + e.getMessage(), e);
        }
    }

    /**
     * 构建钉钉 webhook payload，格式与通用 webhook 一致：msgtype=text。
     */
    private String buildPayload(String content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("msgtype", "text");
        payload.put("text", Map.of("content", content));
        try {
            return JSON.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to build DingTalk webhook payload", e);
        }
    }
}
