package com.oryxos.core.react;

import com.oryxos.core.agent.AuditPort;
import com.oryxos.core.model.OryxTool;
import com.oryxos.core.model.Session;
import com.oryxos.core.model.ToolCallRequest;
import com.oryxos.core.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolExecutorTest {

    @Mock AuditPort auditPort;

    private ToolExecutor executor;
    private OryxTool mockTool;
    private Session session;

    @BeforeEach
    void setUp() {
        mockTool = mock(OryxTool.class);
        when(mockTool.getName()).thenReturn("read_file");
        // Refactored constructor: (AuditPort) — sandbox removed
        executor = new ToolExecutor(auditPort);
        session = new Session("s1", "agent1", "cli", "u1");
    }

    @Test
    void toolNotFound_returnsFailNotRetryable() {
        ToolCallRequest req = new ToolCallRequest("c1", "unknown_tool", Map.of());
        ToolResult result = executor.execute(session, req, List.of(mockTool));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isRetryable()).isFalse();
        assertThat(result.getError()).contains("unknown_tool");
    }

    @Test
    void success_returnsOkAndRecordsAudit() {
        ToolCallRequest req = new ToolCallRequest("c1", "read_file", Map.of("path", "/tmp/a.txt"));
        when(mockTool.execute(any())).thenReturn(ToolResult.ok("file content"));

        ToolResult result = executor.execute(session, req, List.of(mockTool));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContent()).isEqualTo("file content");
        verify(auditPort).recordToolInvocation(
                eq("s1"), eq("read_file"), anyString(), eq("file content"),
                eq(true), isNull(), anyLong());
    }

    @Test
    void exceptionCaught_returnsFailRetryable() {
        ToolCallRequest req = new ToolCallRequest("c1", "read_file", Map.of());
        when(mockTool.execute(any())).thenThrow(new RuntimeException("disk error"));

        ToolResult result = executor.execute(session, req, List.of(mockTool));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getError()).contains("disk error");
    }

    @Test
    void failureWithRetryableTrue_propagatesRetryable() {
        ToolCallRequest req = new ToolCallRequest("c1", "read_file", Map.of());
        when(mockTool.execute(any())).thenReturn(ToolResult.fail("timeout", true));

        ToolResult result = executor.execute(session, req, List.of(mockTool));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isRetryable()).isTrue();
    }

    @Test
    void failureWithRetryableFalse_propagatesNotRetryable() {
        ToolCallRequest req = new ToolCallRequest("c1", "read_file", Map.of());
        when(mockTool.execute(any())).thenReturn(ToolResult.fail("bad input", false));

        ToolResult result = executor.execute(session, req, List.of(mockTool));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    void noSandboxCheck_toolExecutorDoesNotReferenceSandbox() {
        // After refactoring, sandbox field is removed. Execute works without sandbox.
        ToolCallRequest req = new ToolCallRequest("c1", "read_file", Map.of());
        when(mockTool.execute(any())).thenReturn(ToolResult.ok("ok"));

        ToolResult result = executor.execute(session, req, List.of(mockTool));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void auditRecorded_withCorrectFields() {
        ToolCallRequest req = new ToolCallRequest("c1", "read_file", Map.of("path", "/tmp/a.txt"));
        when(mockTool.execute(any())).thenReturn(ToolResult.ok("content"));

        executor.execute(session, req, List.of(mockTool));

        verify(auditPort).recordToolInvocation(
                eq("s1"), eq("read_file"), anyString(), eq("content"),
                eq(true), isNull(), anyLong());
    }

    @Test
    void toolThrowsRuntimeException_doesNotPropagate() {
        ToolCallRequest req = new ToolCallRequest("c1", "read_file", Map.of());
        when(mockTool.execute(any())).thenThrow(new RuntimeException("boom"));

        ToolResult result = executor.execute(session, req, List.of(mockTool));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("boom");
    }

    @Test
    void toolThrowsError_alsoCaughtAndReturnedAsFail() {
        ToolCallRequest req = new ToolCallRequest("c1", "read_file", Map.of());
        when(mockTool.execute(any())).thenThrow(new OutOfMemoryError("heap"));

        ToolResult result = executor.execute(session, req, List.of(mockTool));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isRetryable()).isTrue();
    }

    @Test
    void toolReturnsNull_doesNotThrowNPE() {
        ToolCallRequest req = new ToolCallRequest("c1", "read_file", Map.of());
        when(mockTool.execute(any())).thenReturn(null);

        ToolResult result = executor.execute(session, req, List.of(mockTool));

        // Should handle gracefully without NPE
        assertThat(result).isNotNull();
    }
}
