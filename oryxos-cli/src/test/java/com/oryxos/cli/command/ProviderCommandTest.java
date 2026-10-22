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

/** ProviderCommand 单元测试（轻命令，不启动 Spring）。 覆盖 2 个验收点：provider list、空配置处理。 */
@DisplayName("ProviderCommand 测试")
class ProviderCommandTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
        tempDir = Files.createTempDirectory("oryxos-test");
        System.setProperty("user.dir", tempDir.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        System.setOut(originalOut);
        System.setErr(originalErr);
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(f -> f.delete());
        }
    }

    @Test
    @DisplayName("provider list：读取 application.yml 并列出已配置的 Provider")
    void providerListReadsConfigAndListsProviders() throws Exception {
        // Given: 创建包含 Provider 配置的 application.yml
        Path configDir = tempDir.resolve(".oryxos/config");
        Files.createDirectories(configDir);
        String config = "spring:\n  ai:\n    providers:\n      deepseek:\n        api-key: test\n";
        Files.writeString(configDir.resolve("application.yml"), config);

        // When: 执行 provider list
        CommandLine cmd = new CommandLine(new ProviderCommand());
        int exitCode = cmd.execute("list");

        // Then: 输出包含 Provider 名称
        String output = outContent.toString();
        assertTrue(exitCode == 0);
        assertTrue(output.contains("Configured Providers"));
    }

    @Test
    @DisplayName("provider list：无配置文件时提示用户")
    void providerListShowsMessageWhenNoConfig() {
        // Given: 无 .oryxos/ 目录

        // When: 执行 provider list
        CommandLine cmd = new CommandLine(new ProviderCommand());
        int exitCode = cmd.execute("list");

        // Then: 提示无 Provider
        String output = outContent.toString();
        assertTrue(exitCode == 0);
        assertTrue(output.contains("No providers configured"));
    }
}
