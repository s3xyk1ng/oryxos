package com.oryxos.tool.builtin;

import com.oryxos.core.model.Sandbox;
import com.oryxos.core.model.SandboxAction;
import com.oryxos.core.model.ToolResult;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HttpToolsSandboxTest {

    @Test
    @Order(1)
    void httpGet_sandboxEnforcedBeforeRequest() {
        Sandbox sandbox = mock(Sandbox.class);
        HttpTools.HttpGetTool tool = new HttpTools.HttpGetTool(sandbox);

        tool.execute(Map.of("url", "http://example.com/api"));

        verify(sandbox).enforce(argThat(a ->
                a.getType() == SandboxAction.ActionType.HTTP_REQUEST
                        && a.getTarget().equals("http://example.com/api")));
    }

    @Test
    @Order(2)
    void httpPost_sandboxEnforcedBeforeRequest() {
        Sandbox sandbox = mock(Sandbox.class);
        HttpTools.HttpPostTool tool = new HttpTools.HttpPostTool(sandbox);

        tool.execute(Map.of("url", "http://example.com/post", "body", "{}"));

        verify(sandbox).enforce(argThat(a ->
                a.getType() == SandboxAction.ActionType.HTTP_REQUEST
                        && a.getTarget().equals("http://example.com/post")));
    }

    @Test
    @Order(3)
    void sandboxReject_blocksRequest() {
        Sandbox sandbox = mock(Sandbox.class);
        doThrow(new SecurityException("domain not allowed")).when(sandbox).enforce(any());

        HttpTools.HttpGetTool tool = new HttpTools.HttpGetTool(sandbox);
        ToolResult result = tool.execute(Map.of("url", "http://evil.com/hack"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("Sandbox rejected");
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    @Order(4)
    void sandboxOptional_worksWithoutSandbox() {
        HttpTools.HttpGetTool tool = new HttpTools.HttpGetTool(null);

        // Without sandbox, the tool will attempt the actual HTTP call
        // It will fail because the URL doesn't exist, but it should NOT be a sandbox rejection
        ToolResult result = tool.execute(Map.of("url", "http://localhost:19999/nonexistent"));

        // The request should fail with a connection error, not a sandbox rejection
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).doesNotContain("Sandbox rejected");
    }
}
