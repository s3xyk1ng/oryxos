package com.oryxos.tool.builtin;

import com.oryxos.core.model.Sandbox;
import com.oryxos.core.model.SandboxAction;
import com.oryxos.core.model.ToolResult;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BuiltInToolsSandboxOrderTest {

    @Test
    @Order(1)
    void readFile_sandboxBeforeIO() {
        Sandbox sandbox = mock(Sandbox.class);
        FileTools.ReadFileTool tool = new FileTools.ReadFileTool(sandbox);

        tool.execute(Map.of("path", "/tmp/test-order.txt"));

        InOrder inOrder = inOrder(sandbox);
        inOrder.verify(sandbox).enforce(argThat(a ->
                a.getType() == SandboxAction.ActionType.FILE_READ));
    }

    @Test
    @Order(2)
    void shell_sandboxBeforeProcess() {
        Sandbox sandbox = mock(Sandbox.class);
        ShellTools.ShellTool tool = new ShellTools.ShellTool(sandbox);

        tool.execute(Map.of("command", "echo", "args", java.util.List.of("hello")));

        InOrder inOrder = inOrder(sandbox);
        inOrder.verify(sandbox).enforce(argThat(a ->
                a.getType() == SandboxAction.ActionType.SHELL_COMMAND));
    }

    @Test
    @Order(3)
    void httpGet_sandboxBeforeRequest() {
        Sandbox sandbox = mock(Sandbox.class);
        HttpTools.HttpGetTool tool = new HttpTools.HttpGetTool(sandbox);

        tool.execute(Map.of("url", "http://example.com/api"));

        InOrder inOrder = inOrder(sandbox);
        inOrder.verify(sandbox).enforce(argThat(a ->
                a.getType() == SandboxAction.ActionType.HTTP_REQUEST));
    }
}
