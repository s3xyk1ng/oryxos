package com.oryxos.tool.notify;

import com.oryxos.core.model.OryxTool;
import com.oryxos.core.model.ToolResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 通知工具：Agent 可通过此工具发送通知到配置的渠道。 */
@Component
public class NotifyTools {

    private static final Logger log = LoggerFactory.getLogger(NotifyTools.class);

    private final List<NotifyChannelAdapter> adapters;

    public NotifyTools(List<NotifyChannelAdapter> adapters) {
        this.adapters = adapters;
    }

    @Component("notifyTool")
    public class NotifyTool implements OryxTool {
        @Override
        public String getName() {
            return "notify";
        }

        @Override
        public String getDescription() {
            return "Send a notification message to a specified channel via webhook URL.";
        }

        @Override
        public Map<String, Object> getInputSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            Map<String, Object> props = new LinkedHashMap<>();
            props.put(
                    "content",
                    Map.of("type", "string", "description", "The notification message content"));
            props.put(
                    "channel",
                    Map.of(
                            "type",
                            "string",
                            "description",
                            "The webhook URL or channel name to send to"));
            schema.put("properties", props);
            schema.put("required", List.of("content", "channel"));
            return schema;
        }

        @Override
        public ToolResult execute(Map<String, Object> input) {
            String content = (String) input.get("content");
            String channel = (String) input.get("channel");
            if (content == null || content.isBlank()) {
                return ToolResult.fail("content parameter is required", false);
            }
            if (channel == null || channel.isBlank()) {
                return ToolResult.fail("channel parameter is required", false);
            }

            // Find webhook adapter
            NotifyChannelAdapter adapter =
                    adapters.stream()
                            .filter(a -> "webhook".equals(a.getType()))
                            .findFirst()
                            .orElse(null);

            if (adapter == null) {
                return ToolResult.fail("No webhook notification adapter available", false);
            }

            String result = adapter.send(channel, content);
            return ToolResult.ok(result);
        }
    }
}
