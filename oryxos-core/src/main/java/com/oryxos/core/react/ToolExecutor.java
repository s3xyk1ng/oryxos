package com.oryxos.core.react;

import com.oryxos.core.agent.AuditPort;
import com.oryxos.core.model.*;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 工具执行器：从 ToolRegistry 查找 Tool → Sandbox 检查 → 执行 → 包装 ToolResult → 审计。 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final Sandbox sandbox;
    private final AuditPort auditPort;

    public ToolExecutor(@Autowired(required = false) Sandbox sandbox, AuditPort auditPort) {
        this.sandbox = sandbox;
        this.auditPort = auditPort;
    }

    /** 执行一次工具调用。 */
    public ToolResult execute(Session session, ToolCallRequest request, List<OryxTool> tools) {
        String toolName = request.getToolName();
        Map<String, Object> input = request.getArguments();

        // Find tool
        OryxTool tool =
                tools.stream().filter(t -> t.getName().equals(toolName)).findFirst().orElse(null);

        if (tool == null) {
            String error = "Tool not found: " + toolName;
            log.warn(error);
            return ToolResult.fail(error, false);
        }

        // Sandbox check (skip if no sandbox configured)
        if (sandbox != null) {
            try {
                performSandboxCheck(tool, input);
            } catch (SecurityException e) {
                log.warn("Sandbox rejected tool {}: {}", toolName, e.getMessage());
                return ToolResult.fail("Sandbox rejected: " + e.getMessage(), false);
            }
        }

        // Execute
        long start = System.currentTimeMillis();
        ToolResult result;
        try {
            result = tool.execute(input);
        } catch (Exception e) {
            log.error("Tool {} execution failed: {}", toolName, e.getMessage());
            result = ToolResult.fail("Execution error: " + e.getMessage(), true);
        }
        long duration = System.currentTimeMillis() - start;

        // Audit
        try {
            String inputJson = mapToJson(input);
            String resultJson = result.isSuccess() ? result.getContent() : result.getError();
            auditPort.recordToolInvocation(
                    session.getSessionId(),
                    toolName,
                    inputJson,
                    resultJson,
                    result.isSuccess(),
                    result.isSuccess() ? null : result.getError(),
                    duration);
        } catch (Exception e) {
            log.warn("Failed to record tool audit: {}", e.getMessage());
        }

        return result;
    }

    private void performSandboxCheck(OryxTool tool, Map<String, Object> input) {
        String name = tool.getName();
        // File operations
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
        // Other tools (memory, notify, mcp) pass through sandbox
    }

    private String mapToJson(Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return map.toString();
        }
    }
}
