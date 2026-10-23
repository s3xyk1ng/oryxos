package com.oryxos.tool.builtin;

import com.oryxos.core.model.OryxTool;
import com.oryxos.core.model.Sandbox;
import com.oryxos.core.model.SandboxAction;
import com.oryxos.core.model.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内置 HTTP 工具：http_get 和 http_post。
 */
@Component
public class HttpTools {

    private static final Logger log = LoggerFactory.getLogger(HttpTools.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Component("httpGetTool")
    public static class HttpGetTool implements OryxTool {

        private final Sandbox sandbox;

        public HttpGetTool(@Autowired(required = false) Sandbox sandbox) {
            this.sandbox = sandbox;
        }

        @Override
        public String getName() { return "http_get"; }

        @Override
        public String getDescription() { return "Send an HTTP GET request to a URL and return the response body."; }

        @Override
        public Map<String, Object> getInputSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("url", Map.of("type", "string", "description", "The URL to send GET request to"));
            schema.put("properties", props);
            schema.put("required", java.util.List.of("url"));
            return schema;
        }

        @Override
        public ToolResult execute(Map<String, Object> input) {
            String url = (String) input.get("url");
            if (url == null || url.isBlank()) {
                return ToolResult.fail("url parameter is required", false);
            }
            if (sandbox != null) {
                try {
                    sandbox.enforce(new SandboxAction(SandboxAction.ActionType.HTTP_REQUEST, url));
                } catch (SecurityException e) {
                    return ToolResult.fail("Sandbox rejected: " + e.getMessage(), false);
                }
            }
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return ToolResult.ok(response.body());
                } else {
                    return ToolResult.fail("HTTP " + response.statusCode() + ": " + response.body(), true);
                }
            } catch (Exception e) {
                return ToolResult.fail("HTTP GET failed: " + e.getMessage(), true);
            }
        }
    }

    @Component("httpPostTool")
    public static class HttpPostTool implements OryxTool {

        private final Sandbox sandbox;

        public HttpPostTool(@Autowired(required = false) Sandbox sandbox) {
            this.sandbox = sandbox;
        }

        @Override
        public String getName() { return "http_post"; }

        @Override
        public String getDescription() { return "Send an HTTP POST request with a JSON body."; }

        @Override
        public Map<String, Object> getInputSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("url", Map.of("type", "string", "description", "The URL to send POST request to"));
            props.put("body", Map.of("type", "string", "description", "The request body (JSON string)"));
            schema.put("properties", props);
            schema.put("required", java.util.List.of("url"));
            return schema;
        }

        @Override
        public ToolResult execute(Map<String, Object> input) {
            String url = (String) input.get("url");
            String body = (String) input.getOrDefault("body", "");
            if (url == null || url.isBlank()) {
                return ToolResult.fail("url parameter is required", false);
            }
            if (sandbox != null) {
                try {
                    sandbox.enforce(new SandboxAction(SandboxAction.ActionType.HTTP_REQUEST, url));
                } catch (SecurityException e) {
                    return ToolResult.fail("Sandbox rejected: " + e.getMessage(), false);
                }
            }
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return ToolResult.ok(response.body());
                } else {
                    return ToolResult.fail("HTTP " + response.statusCode() + ": " + response.body(), true);
                }
            } catch (Exception e) {
                return ToolResult.fail("HTTP POST failed: " + e.getMessage(), true);
            }
        }
    }
}
