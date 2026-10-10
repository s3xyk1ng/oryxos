package com.oryxos.core.react;

import com.oryxos.core.context.ContextLoader;
import com.oryxos.core.model.Message;
import com.oryxos.core.model.OryxTool;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Prompt 构建器：四部分拼接——
 * ① system prompt（AGENT.md 正文 + Bootstrap + Skill 元数据 + 当前日期时间）
 * ② Memory 注入（通过 MemoryPort，由 oryxos-memory 实现）
 * ③ 对话历史（按 maxHistoryTurns 截断）
 * ④ 可用 Tool 列表（Function Calling 格式）
 */
@Component
public class PromptBuilder {

    private final ContextLoader contextLoader;
    private final MemoryPort memoryPort;

    public PromptBuilder(ContextLoader contextLoader,
                         @Autowired(required = false) MemoryPort memoryPort) {
        this.contextLoader = contextLoader;
        this.memoryPort = memoryPort;
    }

    /**
     * 构建 system prompt。
     */
    public String buildSystemPrompt(Profile profile, Session session) {
        StringBuilder sb = new StringBuilder();

        // Bootstrap files
        String bootstrap = contextLoader.loadBootstrap();
        if (!bootstrap.isBlank()) {
            sb.append(bootstrap).append("\n\n");
        }

        // Agent body
        String agentBody = contextLoader.loadAgentBody(profile.getName());
        if (!agentBody.isBlank()) {
            sb.append(agentBody).append("\n\n");
        }

        // Skills metadata
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

        // Current date/time
        sb.append("Current time: ")
                .append(ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME));

        // Memory injection (if available)
        if (memoryPort != null) {
            String memoryContext = memoryPort.getMemoryContext();
            if (memoryContext != null && !memoryContext.isBlank()) {
                sb.append("\n\n").append(memoryContext);
            }
        }

        return sb.toString();
    }

    /**
     * 构建 Spring AI Prompt（包含对话历史）。
     */
    public Prompt buildChatPrompt(String systemPrompt, List<Message> history, List<OryxTool> tools) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }

        for (Message msg : history) {
            switch (msg.getRole()) {
                case "user" -> messages.add(new UserMessage(msg.getContent()));
                case "assistant" -> messages.add(new AssistantMessage(msg.getContent()));
                case "system" -> messages.add(new SystemMessage(msg.getContent()));
                case "tool" -> messages.add(new AssistantMessage("[Tool Result] " + msg.getContent()));
            }
        }

        return new Prompt(messages);
    }
}
