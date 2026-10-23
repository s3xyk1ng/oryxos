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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileToolsSandboxTest {

    @Test
    @Order(1)
    void readFile_sandboxEnforcedBeforeIO() {
        Sandbox sandbox = mock(Sandbox.class);
        FileTools.ReadFileTool tool = new FileTools.ReadFileTool(sandbox);

        tool.execute(Map.of("path", "/tmp/test.txt"));

        verify(sandbox).enforce(argThat(a ->
                a.getType() == SandboxAction.ActionType.FILE_READ
                        && a.getTarget().equals("/tmp/test.txt")));
    }

    @Test
    @Order(2)
    void writeFile_sandboxEnforcedBeforeIO() {
        Sandbox sandbox = mock(Sandbox.class);
        FileTools.WriteFileTool tool = new FileTools.WriteFileTool(sandbox);

        tool.execute(Map.of("path", "/tmp/out.txt", "content", "data"));

        verify(sandbox).enforce(argThat(a ->
                a.getType() == SandboxAction.ActionType.FILE_WRITE
                        && a.getTarget().equals("/tmp/out.txt")));
    }

    @Test
    @Order(3)
    void sandboxReject_blocksIO() throws Exception {
        Sandbox sandbox = mock(Sandbox.class);
        doThrow(new SecurityException("denied")).when(sandbox).enforce(any());

        FileTools.WriteFileTool tool = new FileTools.WriteFileTool(sandbox);
        Path tempFile = Files.createTempFile("sandbox-test", ".txt");

        ToolResult result = tool.execute(Map.of("path", tempFile.toString(), "content", "hack"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("Sandbox rejected");
        assertThat(result.isRetryable()).isFalse();
        // File should NOT have been modified
        assertThat(Files.readString(tempFile)).isEmpty();
        Files.deleteIfExists(tempFile);
    }

    @Test
    @Order(4)
    void sandboxOptional_worksWithoutSandbox() throws Exception {
        FileTools.ReadFileTool tool = new FileTools.ReadFileTool(null);
        Path tempFile = Files.createTempFile("no-sandbox", ".txt");
        Files.writeString(tempFile, "hello");

        ToolResult result = tool.execute(Map.of("path", tempFile.toString()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContent()).isEqualTo("hello");
        Files.deleteIfExists(tempFile);
    }

    @Test
    @Order(5)
    void listDir_sandboxEnforced() {
        Sandbox sandbox = mock(Sandbox.class);
        FileTools.ListDirTool tool = new FileTools.ListDirTool(sandbox);

        tool.execute(Map.of("path", "/tmp"));

        verify(sandbox).enforce(argThat(a ->
                a.getType() == SandboxAction.ActionType.FILE_READ
                        && a.getTarget().equals("/tmp")));
    }
}
