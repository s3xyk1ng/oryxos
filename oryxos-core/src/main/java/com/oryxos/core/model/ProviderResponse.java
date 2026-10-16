package com.oryxos.core.model;

import java.util.List;

/** LLM 调用返回结果。content 为文本内容（可能为空）， toolCalls 为原始 tool_call 列表（Provider 层不执行）。 */
public record ProviderResponse(String content, List<ToolCall> toolCalls, Usage usage) {

    /** 是否包含 tool_call。 */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
