package com.oryxos.core.react;

import com.oryxos.core.agent.ProviderService;
import com.oryxos.core.model.*;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/**
 * ReAct 循环引擎：Agent 大脑。 驱动 Reason-Act 循环：组装 Prompt → 调用 LLM → 判断是否有工具调用 → 执行工具 → 回填结果 → 继续循环。 严格
 * maxIterations 限制，达到上限强制终止。
 */
@Component
public class ReActLoop {

    private static final Logger log = LoggerFactory.getLogger(ReActLoop.class);

    private final PromptBuilder promptBuilder;
    private final ProviderService providerService;
    private final ToolExecutor toolExecutor;

    public ReActLoop(
            PromptBuilder promptBuilder,
            ProviderService providerService,
            ToolExecutor toolExecutor) {
        this.promptBuilder = promptBuilder;
        this.providerService = providerService;
        this.toolExecutor = toolExecutor;
    }

    /**
     * 执行 ReAct 循环。
     *
     * @param session 当前会话
     * @param profile Agent 配置
     * @param message 用户消息（首次传入，后续迭代传 null）
     * @return LLM 最终响应
     */
    public ProviderResponse run(Session session, Profile profile, String message) {
        // 1. 用户消息追加到会话（首次传入，后续 null 不追加）
        if (message != null && !message.isBlank()) {
            session.addMessage(new Message("user", message));
        }

        int maxIterations = profile.getMaxIterations();
        ProviderResponse response = null;

        // 2. 循环推理，严格控制最大次数
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            log.debug(
                    "ReAct iteration {}/{} for session {}",
                    iteration + 1,
                    maxIterations,
                    session.getSessionId());

            // 组装 Prompt（委托 PromptBuilder）
            org.springframework.ai.chat.prompt.Prompt prompt =
                    promptBuilder.build(profile, session);

            // 调用 LLM（委托 ProviderService，内置审计）
            response = providerService.chat(session.getSessionId(), profile, toOryxPrompt(prompt));

            // 助手响应追加到会话历史
            session.addMessage(new Message("assistant", response.content()));

            // 无 tool_calls → 推理结束
            if (!response.hasToolCalls()) {
                log.debug("No tool calls at iteration {}, loop ends", iteration + 1);
                return response;
            }

            // 有 tool_calls → 逐个执行（委托 ToolExecutor）
            for (ToolCall tc : response.toolCalls()) {
                log.debug("Executing tool: {} (iteration {})", tc.name(), iteration + 1);
                ToolResult result = toolExecutor.execute(session, tc);
                String resultContent =
                        result.isSuccess() ? result.getContent() : "Error: " + result.getError();
                session.addMessage(
                        new Message("tool", "[" + tc.name() + "] " + resultContent, tc.id()));
            }
        }

        // 3. 达到最大轮数，强制返回
        log.warn("Max iterations ({}) reached, session={}", maxIterations, session.getSessionId());
        return response;
    }

    /** 将 Spring AI Prompt 转为 OryxOS Prompt（提取消息和工具列表）。 */
    private Prompt toOryxPrompt(org.springframework.ai.chat.prompt.Prompt springPrompt) {
        // ProviderService 接收 OryxOS 原生 Prompt 类型
        // 这里将 Spring AI Prompt 的消息提取为 OryxOS Message 列表
        // 工具列表由 ProviderService 通过 ToolRegistry 获取
        List<Message> messages =
                springPrompt.getInstructions().stream()
                        .map(
                                m -> {
                                    String role;
                                    if (m instanceof UserMessage) {
                                        role = "user";
                                    } else if (m instanceof AssistantMessage) {
                                        role = "assistant";
                                    } else {
                                        // SystemMessage 及其他未知类型统一映射为 system
                                        role = "system";
                                    }
                                    return new Message(role, m.getText());
                                })
                        .toList();
        return new Prompt(messages, null);
    }
}
