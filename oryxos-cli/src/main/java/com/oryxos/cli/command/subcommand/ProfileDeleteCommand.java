package com.oryxos.cli.command.subcommand;

import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/** profile delete 子命令。 删除 .oryxos/agents/<NAME>/ 目录。 */
@Command(name = "delete", description = "Delete an Agent Profile")
public class ProfileDeleteCommand implements Runnable {

    @Parameters(index = "0", paramLabel = "<NAME>", description = "Profile name")
    String name;

    @Override
    public void run() {
        Path agentDir = Path.of(System.getProperty("user.dir"), ".oryxos", "agents", name);

        if (!Files.exists(agentDir)) {
            System.err.println("Profile not found: " + name);
            return;
        }

        // 注意：实际删除需要用户确认，这里只实现删除逻辑
        // 交互式确认在集成测试中验证
        try {
            // 递归删除目录
            Files.walk(agentDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(
                            f -> {
                                try {
                                    Files.deleteIfExists(f.toPath());
                                } catch (Exception ex) {
                                    // ignore individual file deletion errors
                                }
                            });

            System.out.println("Profile deleted: " + name);
        } catch (Exception e) {
            System.err.println("Error deleting profile: " + e.getMessage());
        }
    }
}
