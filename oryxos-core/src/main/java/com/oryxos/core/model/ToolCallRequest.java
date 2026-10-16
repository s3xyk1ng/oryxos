package com.oryxos.core.model;

import java.util.Map;

/** LLM 返回的工具调用请求。 由 FunctionCallingAdapter 从 LLM 响应中解析。 */
public class ToolCallRequest {

    private String id;
    private String toolName;
    private Map<String, Object> arguments;

    public ToolCallRequest() {}

    public ToolCallRequest(String id, String toolName, Map<String, Object> arguments) {
        this.id = id;
        this.toolName = toolName;
        this.arguments = arguments;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }
}
