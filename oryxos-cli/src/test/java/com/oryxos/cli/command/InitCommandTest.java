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

/** InitCommand 单元测试（轻命令，不启动 Spring）。 覆盖 2 个验收点：创建目录结构、幂等性。 */
@DisplayName("InitCommand 测试")
class InitCommandTest {

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
    @DisplayName("init 创建 .oryxos/ 目录结构")
    void initCreatesDirectoryStructure() {
        // Given: 空工作区

        // When: 执行 init
        CommandLine cmd = new CommandLine(new InitCommand());
        int exitCode = cmd.execute();

        // Then: .oryxos/ 目录已创建
        assertTrue(exitCode == 0);
        assertTrue(Files.exists(tempDir.resolve(".oryxos")));
        assertTrue(Files.exists(tempDir.resolve(".oryxos/agents")));
    }

    @Test
    @DisplayName("init 幂等性：重复执行不覆盖已有文件")
    void initIdempotentDoesNotOverwriteExistingFiles() throws Exception {
        // Given: 已存在 .oryxos/ 目录
        Files.createDirectories(tempDir.resolve(".oryxos/agents"));

        // When: 再次执行 init
        CommandLine cmd = new CommandLine(new InitCommand());
        int exitCode = cmd.execute();

        // Then: 不报错，目录仍存在
        assertTrue(exitCode == 0);
        assertTrue(Files.exists(tempDir.resolve(".oryxos/agents")));
    }
}
