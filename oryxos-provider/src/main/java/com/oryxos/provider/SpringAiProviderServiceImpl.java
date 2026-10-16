package com.oryxos.provider;

import com.oryxos.core.agent.ProviderRegistry;
import com.oryxos.core.agent.ProviderService;
import com.oryxos.core.model.Message;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Prompt;
import com.oryxos.core.model.ProviderCallException;
import com.oryxos.core.model.ProviderDef;
import com.oryxos.core.model.ProviderResponse;
import com.oryxos.core.model.ToolCall;
import com.oryxos.core.model.Usage;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * ProviderService 核心实现。协调 Registry → Factory → ChatModel → ToolSchemaAdapter → AuditLogger。 只用
 * ChatModel.call()，不用 ChatClient/Advisor。
 */
public class SpringAiProviderServiceImpl implements ProviderService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiProviderServiceImpl.class);

    private final ProviderRegistry registry;
    private final ProviderChatModelFactory factory;
    private final ToolSchemaAdapter toolSchemaAdapter;
    private final AuditLogger auditLogger;

    public SpringAiProviderServiceImpl(
            ProviderRegistry registry,
            ProviderChatModelFactory factory,
            ToolSchemaAdapter toolSchemaAdapter,
            AuditLogger auditLogger) {
        this.registry = registry;
        this.factory = factory;
        this.toolSchemaAdapter = toolSchemaAdapter;
        this.auditLogger = auditLogger;
    }

    @Override
    public ProviderResponse chat(String sessionId, Profile profile, Prompt prompt) {
        // 1. 查 Provider 定义
        ProviderDef def = registry.find(profile.getProvider());

        // 2. 获取/构建 ChatModel（指纹缓存）
        ChatModel chatModel = factory.resolve(def);

        // 3. 构建 ChatOptions（按 type switch + 合并 Profile 配置）
        ChatOptions options = buildOptions(def, profile);

        // 4. 构建 Spring AI Prompt
        List<org.springframework.ai.chat.messages.Message> springMessages =
                toSpringMessages(prompt);
        org.springframework.ai.chat.prompt.Prompt springPrompt =
                new org.springframework.ai.chat.prompt.Prompt(springMessages, options);

        // 5. 调用 LLM
        long start = System.currentTimeMillis();
        try {
            ChatResponse response = chatModel.call(springPrompt);

            // 6. 提取结果
            String content = toolSchemaAdapter.extractContent(response);
            List<ToolCall> toolCalls = toolSchemaAdapter.extractToolCalls(response);
            Usage usage = extractUsage(response);

            // 7. 审计记录（成功）
            long duration = System.currentTimeMillis() - start;
            auditLogger.logSuccess(
                    sessionId,
                    profile.getName(),
                    def.name(),
                    profile.getModel(),
                    usage,
                    toolCalls.size(),
                    duration);

            return new ProviderResponse(content, toolCalls, usage);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            auditLogger.logFailure(
                    sessionId,
                    profile.getName(),
                    def.name(),
                    profile.getModel(),
                    duration,
                    e.getMessage());
            throw new ProviderCallException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    /** 按 provider type 构建 ChatOptions，合并 Profile 的 model/temperature。 */
    ChatOptions buildOptions(ProviderDef def, Profile profile) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();

        if (profile.getModel() != null) {
            builder.model(profile.getModel());
        }
        if (profile.getTemperature() != null) {
            builder.temperature(profile.getTemperature());
        }

        return builder.build();
    }

    private List<org.springframework.ai.chat.messages.Message> toSpringMessages(Prompt prompt) {
        List<org.springframework.ai.chat.messages.Message> result = new ArrayList<>();
        for (Message msg : prompt.messages()) {
            switch (msg.getRole()) {
                case "user":
                    result.add(
                            new org.springframework.ai.chat.messages.UserMessage(msg.getContent()));
                    break;
                case "assistant":
                    result.add(
                            new org.springframework.ai.chat.messages.AssistantMessage(
                                    msg.getContent()));
                    break;
                case "system":
                    result.add(
                            new org.springframework.ai.chat.messages.SystemMessage(
                                    msg.getContent()));
                    break;
                default:
                    log.warn("Unknown message role: {}, treating as user message", msg.getRole());
                    result.add(
                            new org.springframework.ai.chat.messages.UserMessage(msg.getContent()));
                    break;
            }
        }
        return result;
    }

    private Usage extractUsage(ChatResponse response) {
        ChatResponseMetadata metadata = response.getMetadata();
        if (metadata != null && metadata.getUsage() != null) {
            var u = metadata.getUsage();
            int prompt = u.getPromptTokens() != null ? u.getPromptTokens() : 0;
            int completion = u.getCompletionTokens() != null ? u.getCompletionTokens() : 0;
            int total = u.getTotalTokens() != null ? u.getTotalTokens() : (prompt + completion);
            return new Usage(prompt, completion, total);
        }
        return new Usage(0, 0, 0);
    }
}
