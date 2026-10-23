package com.oryxos.tool.notify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.context.ProfileContext;
import com.oryxos.core.model.ConditionalTool;
import com.oryxos.core.model.OryxTool;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.ToolResult;
import com.oryxos.storage.entity.NotifyChannelEntity;
import com.oryxos.storage.repository.NotifyChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 通知工具：独立 @Component，实现 OryxTool + ConditionalTool。
 * 渠道表为空时 isAvailable() 返回 false，ToolAutoRegistrar 不注册，LLM 看不到该工具。
 */
@Component
public class NotifyTool implements OryxTool, ConditionalTool {

    private static final Logger log = LoggerFactory.getLogger(NotifyTool.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final NotifyChannelRepository channelRepository;
    private final List<NotifyChannelAdapter> adapters;

    public NotifyTool(NotifyChannelRepository channelRepository,
                      List<NotifyChannelAdapter> adapters) {
        this.channelRepository = channelRepository;
        this.adapters = adapters;
    }

    @Override
    public boolean isAvailable() {
        return channelRepository.count() > 0;
    }

    @Override
    public String getName() {
        return "notify";
    }

    @Override
    public String getDescription() {
        return "Send a notification message to a registered channel. "
                + "If channel is not specified, the default channel from profile is used.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("content", Map.of("type", "string",
                "description", "The notification message content"));
        props.put("channel", Map.of("type", "string",
                "description",
                "The registered channel name (optional, uses profile default if omitted)"));
        schema.put("properties", props);
        schema.put("required", List.of("content"));
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> input) {
        String content = (String) input.get("content");
        String channelName = (String) input.get("channel");

        // ① 参数校验：content 非空
        if (content == null || content.isBlank()) {
            return ToolResult.fail("content parameter is required", false);
        }

        // ② 渠道名解析：LLM 没传 → 取 Profile 默认值
        if (channelName == null || channelName.isBlank()) {
            Profile profile = ProfileContext.get();
            if (profile != null) {
                channelName = profile.getDefaultNotifyChannel();
            }
        }
        if (channelName == null || channelName.isBlank()) {
            return ToolResult.fail(
                    "No notification channel specified and no default configured.", false);
        }

        // ③ 从全局注册表查渠道
        Optional<NotifyChannelEntity> entity = channelRepository.findByName(channelName);
        if (entity.isEmpty()) {
            return ToolResult.fail("Channel not found: " + channelName, false);
        }

        // ④ 构建 NotifyTarget（含 ${ENV_VAR} 解析）
        NotifyTarget target = buildTarget(entity.get());

        // ⑤ 遍历适配器，谁支持谁处理
        for (NotifyChannelAdapter adapter : adapters) {
            String result = adapter.send(target, content);
            if (result != null && !result.startsWith("unsupported")) {
                return ToolResult.ok(result);
            }
        }

        // 所有适配器都不支持该类型
        return ToolResult.fail(
                "No adapter supports channel type: " + target.type(), false);
    }

    /**
     * 从 NotifyChannelEntity 构建 NotifyTarget。
     */
    NotifyTarget buildTarget(NotifyChannelEntity entity) {
        Map<String, String> config = new HashMap<>();
        if (entity.getUrl() != null) {
            config.put("url", entity.getUrl());
        }
        if (entity.getConfigJson() != null && !entity.getConfigJson().isBlank()) {
            try {
                Map<String, String> extra = JSON.readValue(
                        entity.getConfigJson(), new TypeReference<Map<String, String>>() {});
                extra.forEach((k, v) -> config.put(k, resolveEnvVar(v)));
            } catch (Exception e) {
                log.warn("Failed to parse configJson for channel '{}': {}",
                        entity.getName(), e.getMessage());
            }
        }
        return new NotifyTarget(entity.getType(), config);
    }

    /**
     * 解析 ${ENV_VAR} 占位符为环境变量值。
     */
    String resolveEnvVar(String value) {
        if (value == null) {
            return null;
        }
        String result = value;
        int start = result.indexOf("${");
        while (start >= 0) {
            int end = result.indexOf("}", start);
            if (end < 0) {
                break;
            }
            String envName = result.substring(start + 2, end);
            String envValue = System.getenv(envName);
            if (envValue != null) {
                result = result.substring(0, start) + envValue + result.substring(end + 1);
            }
            start = result.indexOf("${", end);
        }
        return result;
    }
}
