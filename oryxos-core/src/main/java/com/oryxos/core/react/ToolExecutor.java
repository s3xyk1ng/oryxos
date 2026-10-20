package com.oryxos.core.react;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.agent.AuditPort;
import com.oryxos.core.agent.ToolRegistry;
import com.oryxos.core.model.*;
import java.util.Collections;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 工具执行器：从 ToolRegistry 查找 Tool → Sandbox 检查 → 执行 → 审计。 审计在 finally 块中完成，无论成功/失败/找不到/沙箱拒绝都写一条
 * tool_invocations 记录。
 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final ToolRegistry toolRegistry;
    private final Sandbox sandbox;
    private final AuditPort auditPort;
    private final ObjectMapper objectMapper;

    public ToolExecutor(
            ToolRegistry toolRegistry,
            @Autowired(required = false) Sandbox sandbox,
            AuditPort auditPort) {
        this.toolRegistry = toolRegistry;
        this.sandbox = sandbox;
        this.auditPort = auditPort;
        this.objectMapper = new ObjectMapper();
    }

    /** 执行一次工具调用，无论成败都记录审计。 */
    public ToolResult execute(Session session, ToolCall toolCall) {
        String toolName = toolCall.name();
        String toolCallId = toolCall.id();
        Map<String, Object> input = parseArguments(toolCall.arguments());
        long start = System.currentTimeMillis();
        ToolResult result = null;

        try {
            // 1. 从 ToolRegistry 查找工具
            OryxTool tool = toolRegistry.getTool(toolName);
            if (tool == null) {
                result = ToolResult.fail("Tool not found: " + toolName, false);
                return result;
            }

            // 2. Sandbox 检查（可选）
            if (sandbox != null) {
                try {
                    performSandboxCheck(tool, input);
                } catch (SecurityException e) {
                    log.warn("Sandbox rejected tool {}: {}", toolName, e.getMessage());
                    result = ToolResult.fail("Sandbox rejected: " + e.getMessage(), false);
                    return result;
                }
            }

            // 3. 执行工具
            try {
                result = tool.execute(input);
            } catch (Exception e) {
                log.error("Tool {} execution failed: {}", toolName, e.getMessage());
                result = ToolResult.fail("Execution error: " + e.getMessage(), true);
            }
            return result;
        } finally {
            // 4. 审计：无论成败，四种情况全覆盖
            long duration = System.currentTimeMillis() - start;
            try {
                String inputJson = mapToJson(input);
                String resultJson =
                        (result != null)
                                ? (result.isSuccess() ? result.getContent() : result.getError())
                                : "unknown";
                boolean success = (result != null) && result.isSuccess();
                String errorMessage =
                        (result != null && !result.isSuccess()) ? result.getError() : null;
                auditPort.recordToolInvocation(
                        session.getSessionId(),
                        toolCallId,
                        toolName,
                        inputJson,
                        resultJson,
                        success,
                        errorMessage,
                        duration);
            } catch (Exception e) {
                log.warn("Failed to record tool audit: {}", e.getMessage());
            }
        }
    }

    /** 解析 ToolCall 的 JSON arguments 为 Map。 */
    private Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(
                    argumentsJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse tool arguments: {}", argumentsJson);
            return Collections.emptyMap();
        }
    }

    /** Sandbox 安全检查：按工具类型分派到对应的 SandboxAction。 */
    private void performSandboxCheck(OryxTool tool, Map<String, Object> input) {
        String name = tool.getName();
        if (name.startsWith("read_file") || name.startsWith("list_dir")) {
            String path = (String) input.getOrDefault("path", "");
            sandbox.enforce(new SandboxAction(SandboxAction.ActionType.FILE_READ, path));
        } else if (name.startsWith("write_file")) {
            String path = (String) input.getOrDefault("path", "");
            sandbox.enforce(new SandboxAction(SandboxAction.ActionType.FILE_WRITE, path));
        } else if (name.equals("shell")) {
            String command = (String) input.getOrDefault("command", "");
            sandbox.enforce(new SandboxAction(SandboxAction.ActionType.SHELL_COMMAND, command));
        } else if (name.startsWith("http_")) {
            String url = (String) input.getOrDefault("url", "");
            sandbox.enforce(new SandboxAction(SandboxAction.ActionType.HTTP_REQUEST, url));
        }
        // 其他工具（memory、notify、mcp）直接通过 sandbox
    }

    /** Map 转 JSON 字符串，失败时降级为 toString()。 */
    private String mapToJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return map.toString();
        }
    }
}
