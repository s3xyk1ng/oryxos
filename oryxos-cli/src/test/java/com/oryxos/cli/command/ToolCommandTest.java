package com.oryxos.cli.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** ToolCommand 单元测试（轻命令，不启动 Spring）。 覆盖 2 个验收点：tool list、空目录处理。 */
@DisplayName("ToolCommand 测试")
class ToolCommandTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        System.setOut(new PrintStream(outContent));
        tempDir = Files.createTempDirectory("oryxos-test");
        System.setProperty("user.dir", tempDir.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        System.setOut(originalOut);
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(f -> f.delete());
        }
    }

    @Test
    @DisplayName("tool list：扫描 .oryxos/tools/ 目录并列出 Tool")
    void toolListScansDirectoryAndListsTools() throws Exception {
        // Given: 创建 .oryxos/tools/ 目录和 1 个 Tool
        Path toolsDir = tempDir.resolve(".oryxos/tools/get_weather");
        Files.createDirectories(toolsDir);

        // When: 执行 tool list
        CommandLine cmd = new CommandLine(new ToolCommand());
        int exitCode = cmd.execute("list");

        // Then: 输出包含 Tool 名称
        String output = outContent.toString();
        assertTrue(exitCode == 0);
        assertTrue(output.contains("Available Tools"));
        assertTrue(output.contains("get_weather"));
    }

    @Test
    @DisplayName("tool list：无 tools 目录时提示用户")
    void toolListShowsMessageWhenNoToolsDir() {
        // Given: 无 .oryxos/tools/ 目录

        // When: 执行 tool list
        CommandLine cmd = new CommandLine(new ToolCommand());
        int exitCode = cmd.execute("list");

        // Then: 提示无 Tool
        String output = outContent.toString();
        assertTrue(exitCode == 0);
        assertTrue(output.contains("No tools found"));
    }
}
