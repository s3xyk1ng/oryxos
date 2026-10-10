package com.oryxos.provider;

import com.oryxos.core.model.OryxTool;
import com.oryxos.core.model.ToolCallRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Function Calling 适配器：
 * 1. 将 OryxTool 描述转换为 LLM 可理解的 function 定义
 * 2. 将 LLM 返回的 tool call 解析为内部 ToolCallRequest
 */
@Component
public class FunctionCallingAdapter {

    private static final Logger log = LoggerFactory.getLogger(FunctionCallingAdapter.class);

    /**
     * 从 ChatResponse 中提取 tool call 请求。
     */
    public List<ToolCallRequest> extractToolCalls(ChatResponse response) {
        List<ToolCallRequest> requests = new ArrayList<>();
        if (response == null || response.getResult() == null) return requests;

        AssistantMessage assistantMessage = response.getResult().getOutput();
        if (assistantMessage.getToolCalls() != null) {
            for (AssistantMessage.ToolCall tc : assistantMessage.getToolCalls()) {
                ToolCallRequest request = new ToolCallRequest();
                request.setId(tc.id());
                request.setToolName(tc.name());
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> args = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(tc.arguments(), Map.class);
                    request.setArguments(args);
                } catch (Exception e) {
                    log.warn("Failed to parse tool call arguments for {}: {}", tc.name(), e.getMessage());
                    request.setArguments(Map.of());
                }
                requests.add(request);
            }
        }
        return requests;
    }

    /**
     * 构建基础 Prompt（无工具）。
     */
    public Prompt buildPrompt(String systemPrompt, String userMessage, List<OryxTool> tools) {
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        messages.add(new UserMessage(userMessage));
        return new Prompt(messages);
    }

    /**
     * 构建包含对话历史的 Prompt。
     */
    public Prompt buildPromptWithHistory(String systemPrompt,
                                          List<com.oryxos.core.model.Message> history,
                                          List<OryxTool> tools) {
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        for (com.oryxos.core.model.Message msg : history) {
            switch (msg.getRole()) {
                case "user" -> messages.add(new UserMessage(msg.getContent()));
                case "assistant" -> messages.add(new AssistantMessage(msg.getContent()));
                case "system" -> messages.add(new SystemMessage(msg.getContent()));
                default -> messages.add(new UserMessage(msg.getContent()));
            }
        }
        return new Prompt(messages);
    }
}
