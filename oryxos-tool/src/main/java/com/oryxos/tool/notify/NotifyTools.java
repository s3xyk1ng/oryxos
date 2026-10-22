package com.oryxos.tool.notify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.agent.ToolRegistry;
import com.oryxos.core.context.ProfileContext;
import com.oryxos.core.model.OryxTool;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.ToolResult;
import com.oryxos.storage.entity.NotifyChannelEntity;
import com.oryxos.storage.repository.NotifyChannelRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 通知工具封装：从全局注册表查渠道、构建 NotifyTarget、遍历适配器发送。
 * 内含 NotifyTool（OryxTool 实现），供 ReAct 循环调用。
 */
@Component
public class NotifyTools {

    private static final Logger log = LoggerFactory.getLogger(NotifyTools.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final NotifyChannelRepository channelRepository;
    private final List<NotifyChannelAdapter> adapters;
    private final ToolRegistry toolRegistry;

    private NotifyTool notifyTool;

    public NotifyTools(NotifyChannelRepository channelRepository,
                       List<NotifyChannelAdapter> adapters,
                       ToolRegistry toolRegistry) {
        this.channelRepository = channelRepository;
        this.adapters = adapters;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 启动时检查渠道表：有渠道才注册工具，无渠道则不暴露给 LLM。
     */
    @PostConstruct
    void init() {
        long count = channelRepository.count();
        if (count == 0) {
            log.info("No notify channels configured, notify tool will not be available");
            this.notifyTool = null;
        } else {
            this.notifyTool = new NotifyTool();
            toolRegistry.register(notifyTool);
            log.info("Notify tool registered with {} channel(s) available", count);
        }
    }

    /**
     * 获取 NotifyTool 实例。渠道表为空时返回 null（不注册）。
     */
    public NotifyTool getNotifyTool() {
        return notifyTool;
    }

    /**
     * 从 NotifyChannelEntity 构建 NotifyTarget。
     */
    NotifyTarget buildTarget(NotifyChannelEntity entity) {
        Map<String, String> config = new HashMap<>();
        // url 字段直接放入 config
        if (entity.getUrl() != null) {
            config.put("url", entity.getUrl());
        }
        // configJson 中的扩展配置合并进来
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
     * TODO: 等凭证管理方案确定后完善此方法。
     */
    String resolveEnvVar(String value) {
        if (value == null) {
            return null;
        }
        // 简单替换 ${VAR_NAME} 为对应环境变量
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

    /**
     * 内部类：notify 工具的 OryxTool 实现。
     */
    public class NotifyTool implements OryxTool {

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
            // channel 不是必填，content 必填
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

            // ⑤ TODO: Sandbox 白名单校验（校验在前，发送在后）
            // sandbox.enforce(new SandboxAction(SandboxAction.ActionType.HTTP_REQUEST,
            //         target.config().get("url")));

            // ⑥ 遍历适配器，谁支持谁处理
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
    }
}
