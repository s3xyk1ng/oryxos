package com.oryxos.cli.command.subcommand;

import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/** profile create 子命令。 在 .oryxos/agents/<NAME>/ 下生成 AGENT.md 模板。 */
@Command(name = "create", description = "Create a new Agent Profile")
public class ProfileCreateCommand implements Runnable {

    @Parameters(index = "0", paramLabel = "<NAME>", description = "Profile name")
    String name;

    @Override
    public void run() {
        // 验证名称格式
        if (!name.matches("^[a-zA-Z0-9_-]+$")) {
            System.err.println(
                    "Error: Profile name must contain only letters, numbers, underscores, and hyphens.");
            return;
        }

        Path agentDir = Path.of(System.getProperty("user.dir"), ".oryxos", "agents", name);

        // 幂等性检查：如果已存在，提示并退出
        if (Files.exists(agentDir)) {
            System.out.println("Profile already exists: " + name);
            return;
        }

        try {
            // 创建目录
            Files.createDirectories(agentDir);

            // 生成 AGENT.md 模板
            String template =
                    """
          ---
          name: %s
          description: TODO: Add description
          provider: deepseek
          model: deepseek-chat
          max_iterations: 10
          max_history_turns: 5
          ---

          # %s

          TODO: Add system prompt
          """
                            .formatted(name, name);

            Files.writeString(agentDir.resolve("AGENT.md"), template);

            System.out.println("Profile created: .oryxos/agents/" + name + "/AGENT.md");
            System.out.println("Edit the file to configure your Agent.");
        } catch (Exception e) {
            System.err.println("Error creating profile: " + e.getMessage());
        }
    }
}
