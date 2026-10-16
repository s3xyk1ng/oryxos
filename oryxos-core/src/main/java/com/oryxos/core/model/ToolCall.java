package com.oryxos.core.model;

/** 原始 tool_call 数据。由 LLM 响应中提取，Provider 层不解析不执行， 原样返回给 ReActLoop 调度。 */
public record ToolCall(String id, String name, String arguments) {}
