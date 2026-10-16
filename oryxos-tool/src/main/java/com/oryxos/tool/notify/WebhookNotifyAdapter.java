package com.oryxos.tool.notify;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Webhook 通知适配器：通过 HTTP POST 发送通知。 支持通用 Webhook、企业微信、飞书、钉钉群机器人格式。 */
@Component
public class WebhookNotifyAdapter implements NotifyChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotifyAdapter.class);
    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public String send(String target, String content) {
        try {
            // Build webhook payload (generic JSON format)
            String payload = buildPayload(content);

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(target))
                            .timeout(Duration.ofSeconds(10))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .build();

            HttpResponse<String> response =
                    HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return "Notification sent successfully (HTTP " + response.statusCode() + ")";
            } else {
                return "Notification failed: HTTP "
                        + response.statusCode()
                        + " - "
                        + response.body();
            }
        } catch (Exception e) {
            log.error("Webhook notification failed: {}", e.getMessage());
            return "Notification failed: " + e.getMessage();
        }
    }

    @Override
    public String getType() {
        return "webhook";
    }

    /** 构建通用 Webhook payload。 兼容企业微信/飞书/钉钉的 msgtype=text 格式。 */
    private String buildPayload(String content) {
        // Generic format compatible with most webhook services
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return """
                {"msgtype":"text","text":{"content":"%s"},"content":"%s"}
                """
                .formatted(escaped, escaped)
                .trim();
    }
}
