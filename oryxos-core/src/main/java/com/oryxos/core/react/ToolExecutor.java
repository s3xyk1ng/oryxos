package com.oryxos.core.react;

import com.oryxos.core.agent.AuditPort;
import com.oryxos.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 工具执行器：从工具列表查找 Tool → 执行 → 包装 ToolResult → 审计。
 * Sandbox 校验已下沉到各工具自身，ToolExecutor 不再负责。
 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final AuditPort auditPort;

    public ToolExecutor(AuditPort auditPort) {
        this.auditPort = auditPort;
    }

    /**
     * 执行一次工具调用。
     */
    public ToolResult execute(Session session, ToolCallRequest request, List<OryxTool> tools) {
        String toolName = request.getToolName();
        Map<String, Object> input = request.getArguments();

        // Find tool
        OryxTool tool = tools.stream()
                .filter(t -> t.getName().equals(toolName))
                .findFirst()
                .orElse(null);

        if (tool == null) {
            String error = "Tool not found: " + toolName;
            log.warn(error);
            return ToolResult.fail(error, false);
        }

        // Execute
        long start = System.currentTimeMillis();
        ToolResult result;
        try {
            result = tool.execute(input);
            if (result == null) {
                result = ToolResult.fail("Tool returned null result", true);
            }
        } catch (Throwable e) {
            log.error("Tool {} execution failed: {}", toolName, e.getMessage());
            result = ToolResult.fail("Execution error: " + e.getMessage(), true);
        }
        long duration = System.currentTimeMillis() - start;

        // Audit
        try {
            String inputJson = mapToJson(input);
            String resultJson = result.isSuccess() ? result.getContent() : result.getError();
            auditPort.recordToolInvocation(session.getSessionId(), toolName,
                    inputJson, resultJson, result.isSuccess(),
                    result.isSuccess() ? null : result.getError(), duration);
        } catch (Exception e) {
            log.warn("Failed to record tool audit: {}", e.getMessage());
        }

        return result;
    }

    private String mapToJson(Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return map.toString();
        }
    }
}
