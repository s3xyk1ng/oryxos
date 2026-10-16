package com.oryxos.provider;

import com.oryxos.core.model.ToolCall;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/** OryxTool ↔ Spring AI Function Calling 格式转换。 只生成 schema，不执行工具。 */
public class ToolSchemaAdapter {

    /**
     * 从 ChatResponse 提取原始 tool_calls。
     *
     * @param response Spring AI 响应
     * @return OryxOS 格式的 ToolCall 列表
     */
    public List<ToolCall> extractToolCalls(ChatResponse response) {
        if (response == null || response.getResults() == null) {
            return Collections.emptyList();
        }
        return response.getResults().stream()
                .filter(g -> g.getOutput() != null && g.getOutput().hasToolCalls())
                .flatMap(g -> g.getOutput().getToolCalls().stream())
                .map(tc -> new ToolCall(tc.id(), tc.name(), tc.arguments()))
                .collect(Collectors.toList());
    }

    /**
     * 提取响应中的文本内容。
     *
     * @param response Spring AI 响应
     * @return 文本内容（第一个 generation 的 content）
     */
    public String extractContent(ChatResponse response) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return "";
        }
        Generation first = response.getResults().get(0);
        if (first.getOutput() == null) {
            return "";
        }
        return first.getOutput().getText();
    }
}
