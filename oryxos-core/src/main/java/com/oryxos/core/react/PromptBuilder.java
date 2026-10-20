package com.oryxos.core.react;

import com.oryxos.core.agent.ToolRegistry;
import com.oryxos.core.context.ContextLoader;
import com.oryxos.core.model.Message;
import com.oryxos.core.model.OryxTool;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Prompt 构建器：四部分拼接—— ① system prompt（AGENT.md 正文 + Bootstrap + Skill 元数据 + 当前日期时间） ② Memory 注入（通过
 * MemoryPort，可选） ③ 对话历史（按 maxHistoryTurns 截断） ④ 可用 Tool 列表（Function Calling 格式）
 */
@Component
public class PromptBuilder {

    private static final Logger logger = LoggerFactory.getLogger(PromptBuilder.class);

    private final ContextLoader contextLoader;
    private final MemoryPort memoryPort;
    private final ToolRegistry toolRegistry;

    public PromptBuilder(
            ContextLoader contextLoader,
            @Autowired(required = false) MemoryPort memoryPort,
            ToolRegistry toolRegistry) {
        this.contextLoader = contextLoader;
        this.memoryPort = memoryPort;
        this.toolRegistry = toolRegistry;
    }

    /** 构建完整 Prompt（新版：对接 ReActLoop 重写后的流程）。 */
    public Prompt build(Profile profile, Session session) {
        // ① System Prompt = Bootstrap + AGENT.md + Skills + 当前时间 + Memory
        String systemPrompt = buildSystemPrompt(profile, session);

        // ② Tool schema 追加到 system prompt
        List<OryxTool> tools = toolRegistry.getToolsForProfile(profile);
        if (!tools.isEmpty()) {
            systemPrompt += "\n\n" + buildToolSchema(tools);
        }

        // ③ 对话历史按 maxHistoryTurns 截断（一轮 = user + assistant = 2 条消息）
        List<Message> truncatedHistory =
                truncateHistory(session.getMessages(), profile.getMaxHistoryTurns());

        // ④ 组装 Spring AI Prompt
        return assemblePrompt(systemPrompt, truncatedHistory);
    }

    /** 构建 system prompt：Bootstrap + AGENT.md + Skills + 当前时间 + Memory。 */
    public String buildSystemPrompt(Profile profile, Session session) {
        StringBuilder sb = new StringBuilder();

        // Bootstrap 文件
        String bootstrap = contextLoader.loadBootstrap();
        if (!bootstrap.isBlank()) {
            sb.append(bootstrap).append("\n\n");
        }

        // Agent 指令正文
        String agentBody = contextLoader.loadAgentBody(profile.getName());
        if (!agentBody.isBlank()) {
            sb.append(agentBody).append("\n\n");
        }

        // Skills 元数据
        var skills = contextLoader.loadSkills(profile.getName());
        if (!skills.isEmpty()) {
            sb.append("Available Skills:\n");
            for (var skill : skills) {
                sb.append("- ").append(skill.name());
                if (!skill.description().isBlank()) {
                    sb.append(": ").append(skill.description());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // 当前日期时间（RFC_1123 格式），LLM 不知道今天几号
        sb.append("Current time: ")
                .append(ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME));

        // Memory 注入（可选）
        if (memoryPort != null) {
            String memoryContext = memoryPort.getMemoryContext();
            if (memoryContext != null && !memoryContext.isBlank()) {
                sb.append("\n\n").append(memoryContext);
            }
        }

        return sb.toString();
    }

    /** 生成工具 schema 文本（Function Calling 格式描述）。 */
    private String buildToolSchema(List<OryxTool> tools) {
        StringBuilder sb = new StringBuilder("Available Tools:\n");
        for (OryxTool tool : tools) {
            sb.append("- ").append(tool.getName());
            if (tool.getDescription() != null && !tool.getDescription().isBlank()) {
                sb.append(": ").append(tool.getDescription());
            }
            Map<String, Object> schema = tool.getInputSchema();
            if (schema != null && !schema.isEmpty()) {
                sb.append(" (input schema: ").append(schema).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** 按 maxHistoryTurns 截断对话历史。一轮 = user + assistant = 2 条消息。 */
    private List<Message> truncateHistory(List<Message> messages, int maxHistoryTurns) {
        int maxMessages = maxHistoryTurns * 2;
        if (messages.size() <= maxMessages) {
            return new ArrayList<>(messages);
        }
        // 取最近 maxMessages 条
        return new ArrayList<>(messages.subList(messages.size() - maxMessages, messages.size()));
    }

    /** 组装 Spring AI Prompt，使用传统 switch-case 映射消息角色。 */
    private Prompt assemblePrompt(String systemPrompt, List<Message> history) {
        List<org.springframework.ai.chat.messages.Message> aiMessages = new ArrayList<>();

        // System prompt
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            aiMessages.add(new SystemMessage(systemPrompt));
        }

        // 对话历史：传统 switch-case（禁止增强 switch，P3C/ASM 解析不了）
        for (Message msg : history) {
            String role = msg.getRole();
            switch (role) {
                case "user":
                    aiMessages.add(new UserMessage(msg.getContent()));
                    break;
                case "assistant":
                    aiMessages.add(new AssistantMessage(msg.getContent()));
                    break;
                case "system":
                    aiMessages.add(new SystemMessage(msg.getContent()));
                    break;
                case "tool":
                    // tool 消息映射为 AssistantMessage，让 LLM 看到工具结果
                    aiMessages.add(new AssistantMessage(msg.getContent()));
                    break;
                default:
                    logger.warn("Unknown message role: {}", role);
                    break;
            }
        }

        return new Prompt(aiMessages);
    }
}
