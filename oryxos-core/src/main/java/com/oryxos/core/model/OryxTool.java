package com.oryxos.core.model;

import java.util.Map;

/**
 * 统一工具接口。所有工具——内置 Tool、@Tool 注解的 Plugin Tool、MCP Tool——
 * 必须包装为 OryxTool 实例注册到 ToolRegistry。
 * ReAct 循环不感知 Tool 来源。
 */
public interface OryxTool {

    /** 工具名称（如 http_get、read_file、github_search） */
    String getName();

    /** 工具描述（给 LLM 看的自然语言说明） */
    String getDescription();

    /** 输入参数的 JSON Schema（给 LLM 生成 function calling 参数用） */
    Map<String, Object> getInputSchema();

    /**
     * 执行工具。
     *
     * @param input JSON 格式的参数
     * @return 执行结果
     */
    ToolResult execute(Map<String, Object> input);
}
