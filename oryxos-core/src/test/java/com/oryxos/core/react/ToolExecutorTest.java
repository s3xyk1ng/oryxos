package com.oryxos.core.react;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oryxos.core.agent.AuditPort;
import com.oryxos.core.agent.ToolRegistry;
import com.oryxos.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** ToolExecutor 行为验证：4 种执行场景 + 4 种审计全覆盖 + toolCallId/duration。 */
@ExtendWith(MockitoExtension.class)
class ToolExecutorTest {

    @Mock private ToolRegistry toolRegistry;
    @Mock private Sandbox sandbox;
    @Mock private AuditPort auditPort;
    @Mock private Session session;

    private ToolExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ToolExecutor(toolRegistry, sandbox, auditPort);
        // session.getSessionId() 不能返回 null，否则 anyString() 不匹配
        when(session.getSessionId()).thenReturn("test-session");
    }

    // --- 执行场景 ---

    @Test
    @DisplayName("工具执行成功：返回 ToolResult(success=true)")
    void executeSuccess_returnsSuccessResult() {
        OryxTool tool = mockTool("my_tool", ToolResult.ok("done"));

        ToolCall tc = new ToolCall("tc-1", "my_tool", "{}");
        ToolResult result = executor.execute(session, tc);

        assertTrue(result.isSuccess());
        assertEquals("done", result.getContent());
    }

    @Test
    @DisplayName("工具找不到：返回 ToolResult(success=false, retryable=false)")
    void executeToolNotFound_returnsFailNotRetryable() {
        when(toolRegistry.getTool("missing_tool")).thenReturn(null);

        ToolCall tc = new ToolCall("tc-2", "missing_tool", "{}");
        ToolResult result = executor.execute(session, tc);

        assertFalse(result.isSuccess());
        assertFalse(result.isRetryable());
        assertTrue(result.getError().contains("Tool not found: missing_tool"));
    }

    @Test
    @DisplayName("Sandbox 拒绝：返回 ToolResult(success=false, retryable=false)")
    void executeSandboxRejected_returnsFailNotRetryable() {
        OryxTool tool = mock(OryxTool.class);
        when(tool.getName()).thenReturn("http_get");
        when(toolRegistry.getTool("http_get")).thenReturn(tool);
        doThrow(new SecurityException("blocked")).when(sandbox).enforce(any());

        ToolCall tc = new ToolCall("tc-3", "http_get", "{\"url\":\"http://bad\"}");
        ToolResult result = executor.execute(session, tc);

        assertFalse(result.isSuccess());
        assertFalse(result.isRetryable());
        assertTrue(result.getError().contains("Sandbox rejected"));
    }

    @Test
    @DisplayName("工具执行抛异常：返回 ToolResult(success=false, retryable=true)")
    void executeException_returnsFailRetryable() {
        OryxTool tool = mockTool("broken_tool", null);
        when(tool.execute(anyMap())).thenThrow(new RuntimeException("boom"));

        ToolCall tc = new ToolCall("tc-4", "broken_tool", "{}");
        ToolResult result = executor.execute(session, tc);

        assertFalse(result.isSuccess());
        assertTrue(result.isRetryable());
        assertTrue(result.getError().contains("boom"));
    }

    // --- 审计覆盖 ---

    @Test
    @DisplayName("成功时审计记录：success=true, errorMessage=null")
    void auditOnSuccess_recordsSuccess() {
        mockTool("my_tool", ToolResult.ok("ok"));

        ToolCall tc = new ToolCall("tc-5", "my_tool", "{}");
        executor.execute(session, tc);

        verify(auditPort)
                .recordToolInvocation(
                        eq("test-session"),
                        eq("tc-5"),
                        eq("my_tool"),
                        anyString(),
                        anyString(),
                        eq(true),
                        isNull(),
                        anyLong());
    }

    @Test
    @DisplayName("找不到时审计记录：success=false, errorMessage 包含 Tool not found")
    void auditOnNotFound_recordsFailure() {
        when(toolRegistry.getTool("missing")).thenReturn(null);

        ToolCall tc = new ToolCall("tc-6", "missing", "{}");
        executor.execute(session, tc);

        verify(auditPort)
                .recordToolInvocation(
                        eq("test-session"),
                        eq("tc-6"),
                        eq("missing"),
                        anyString(),
                        anyString(),
                        eq(false),
                        argThat(msg -> msg != null && msg.contains("Tool not found")),
                        anyLong());
    }

    @Test
    @DisplayName("Sandbox 拒绝时审计记录：success=false")
    void auditOnSandboxRejected_recordsFailure() {
        OryxTool tool = mock(OryxTool.class);
        when(tool.getName()).thenReturn("http_get");
        when(toolRegistry.getTool("http_get")).thenReturn(tool);
        doThrow(new SecurityException("blocked")).when(sandbox).enforce(any());

        ToolCall tc = new ToolCall("tc-7", "http_get", "{}");
        executor.execute(session, tc);

        verify(auditPort)
                .recordToolInvocation(
                        eq("test-session"),
                        eq("tc-7"),
                        eq("http_get"),
                        anyString(),
                        anyString(),
                        eq(false),
                        anyString(),
                        anyLong());
    }

    @Test
    @DisplayName("异常时审计记录：success=false")
    void auditOnException_recordsFailure() {
        OryxTool tool = mockTool("broken", null);
        when(tool.execute(anyMap())).thenThrow(new RuntimeException("err"));

        ToolCall tc = new ToolCall("tc-8", "broken", "{}");
        executor.execute(session, tc);

        verify(auditPort)
                .recordToolInvocation(
                        eq("test-session"),
                        eq("tc-8"),
                        eq("broken"),
                        anyString(),
                        anyString(),
                        eq(false),
                        argThat(msg -> msg != null && msg.contains("err")),
                        anyLong());
    }

    // --- 审计字段 ---

    @Test
    @DisplayName("审计包含 toolCallId：传入正确的 toolCall.id()")
    void auditContainsToolCallId() {
        mockTool("my_tool", ToolResult.ok("ok"));

        ToolCall tc = new ToolCall("unique-id-123", "my_tool", "{}");
        executor.execute(session, tc);

        verify(auditPort)
                .recordToolInvocation(
                        anyString(),
                        eq("unique-id-123"),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyBoolean(),
                        any(),
                        anyLong());
    }

    @Test
    @DisplayName("审计包含 duration：duration_ms >= 0")
    void auditContainsDuration() {
        mockTool("my_tool", ToolResult.ok("ok"));

        ToolCall tc = new ToolCall("tc-10", "my_tool", "{}");
        executor.execute(session, tc);

        // 捕获 duration 参数并验证 >= 0
        verify(auditPort)
                .recordToolInvocation(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyBoolean(),
                        any(),
                        longThat(d -> d >= 0));
    }

    /** 辅助方法：创建 mock OryxTool 并注册到 ToolRegistry。 */
    private OryxTool mockTool(String name, ToolResult result) {
        OryxTool tool = mock(OryxTool.class);
        when(tool.getName()).thenReturn(name);
        if (result != null) {
            when(tool.execute(anyMap())).thenReturn(result);
        }
        when(toolRegistry.getTool(name)).thenReturn(tool);
        return tool;
    }
}
