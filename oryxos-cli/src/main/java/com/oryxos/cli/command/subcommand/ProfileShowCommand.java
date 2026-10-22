package com.oryxos.cli.command.subcommand;

import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/** profile show 子命令。 读取并展示 AGENT.md 内容。 */
@Command(name = "show", description = "Show Profile details")
public class ProfileShowCommand implements Runnable {

    @Parameters(index = "0", paramLabel = "<NAME>", description = "Profile name")
    String name;

    @Override
    public void run() {
        Path agentFile =
                Path.of(System.getProperty("user.dir"), ".oryxos", "agents", name, "AGENT.md");

        if (!Files.exists(agentFile)) {
            System.err.println("Profile not found: " + name);
            return;
        }

        try {
            String content = Files.readString(agentFile);
            System.out.println(content);
        } catch (Exception e) {
            System.err.println("Error reading profile: " + e.getMessage());
        }
    }
}
