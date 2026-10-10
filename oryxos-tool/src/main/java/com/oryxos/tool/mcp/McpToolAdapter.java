package com.oryxos.tool.mcp;

import com.oryxos.core.model.OryxTool;
import com.oryxos.core.model.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 工具适配器：将 MCP Server 提供的工具包装为 OryxTool 接口。
 * 调用时通过 MCP 协议（JSON-RPC）转发给对应 MCP Server。
 */
public class McpToolAdapter implements OryxTool {

    private static final Logger log = LoggerFactory.getLogger(McpToolAdapter.class);

    private final String serverName;
    private final String toolName;
    private final String description;
    private final String inputSchemaJson;
    private final McpClientService mcpClient;

    public McpToolAdapter(String serverName, McpClientService.McpToolInfo toolInfo,
                          McpClientService mcpClient) {
        this.serverName = serverName;
        this.toolName = "mcp_" + serverName + "_" + toolInfo.name();
        this.description = toolInfo.description();
        this.inputSchemaJson = toolInfo.inputSchemaJson();
        this.mcpClient = mcpClient;
    }

    @Override
    public String getName() {
        return toolName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Map<String, Object> getInputSchema() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> schema = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(inputSchemaJson, Map.class);
            return schema;
        } catch (Exception e) {
            // Fallback: return a minimal schema
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", Map.of());
            return schema;
        }
    }

    @Override
    public ToolResult execute(Map<String, Object> input) {
        try {
            String originalToolName = toolName.substring(("mcp_" + serverName + "_").length());
            String result = mcpClient.callTool(serverName, originalToolName, input);
            return ToolResult.ok(result);
        } catch (Exception e) {
            log.error("MCP tool {} execution failed: {}", toolName, e.getMessage());
            return ToolResult.fail("MCP tool execution failed: " + e.getMessage(), true);
        }
    }
}
