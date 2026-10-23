package com.oryxos.tool.builtin;

import com.oryxos.core.model.Sandbox;
import com.oryxos.core.model.SandboxAction;
import com.oryxos.core.model.ToolResult;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShellToolsSandboxTest {

    @Test
    @Order(1)
    void sandboxEnforcedBeforeProcess() {
        Sandbox sandbox = mock(Sandbox.class);
        ShellTools.ShellTool tool = new ShellTools.ShellTool(sandbox);

        tool.execute(Map.of("command", "echo", "args", List.of("hello")));

        verify(sandbox).enforce(argThat(a ->
                a.getType() == SandboxAction.ActionType.SHELL_COMMAND
                        && a.getTarget().equals("echo")));
    }

    @Test
    @Order(2)
    void sandboxReject_blocksExecution() {
        Sandbox sandbox = mock(Sandbox.class);
        doThrow(new SecurityException("command not allowed")).when(sandbox).enforce(any());

        ShellTools.ShellTool tool = new ShellTools.ShellTool(sandbox);
        ToolResult result = tool.execute(Map.of("command", "rm", "args", List.of("-rf", "/")));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("Sandbox rejected");
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    @Order(3)
    void sandboxOptional_worksWithoutSandbox() {
        ShellTools.ShellTool tool = new ShellTools.ShellTool(null);

        ToolResult result = tool.execute(Map.of("command", "cmd", "args", List.of("/c", "echo", "hello")));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContent()).contains("Exit code: 0");
    }
}
