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

/** StatusCommand 单元测试（轻命令，不启动 Spring）。 覆盖 1 个验收点：status 输出显示工作区信息、Agent 数量。 */
@DisplayName("StatusCommand 测试")
class StatusCommandTest {

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
    @DisplayName("status 输出显示工作区信息、Agent 数量")
    void statusOutputShowsWorkspaceInfoAndAgentCount() throws Exception {
        // Given: 创建 .oryxos/ 目录结构和 2 个 Agent
        Path oryxosDir = tempDir.resolve(".oryxos");
        Files.createDirectories(oryxosDir.resolve("agents/weather"));
        Files.createDirectories(oryxosDir.resolve("agents/translator"));
        Files.createDirectories(oryxosDir.resolve("config"));

        // When: 执行 status 命令
        CommandLine cmd = new CommandLine(new StatusCommand());
        int exitCode = cmd.execute();

        // Then: 输出包含工作区信息和 Agent 数量
        String output = outContent.toString();
        assertTrue(exitCode == 0);
        assertTrue(output.contains("OryxOS Status"));
        assertTrue(output.contains("Agents: 2"));
    }
}
