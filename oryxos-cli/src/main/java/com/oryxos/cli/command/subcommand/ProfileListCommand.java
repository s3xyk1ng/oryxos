package com.oryxos.cli.command.subcommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import picocli.CommandLine.Command;

/** profile list 子命令。 扫描 .oryxos/agents/ 目录并列出所有 Agent 名称。 */
@Command(name = "list", description = "List all Agent Profiles")
public class ProfileListCommand implements Runnable {

    @Override
    public void run() {
        Path agentsDir = Path.of(System.getProperty("user.dir"), ".oryxos", "agents");

        if (!Files.exists(agentsDir)) {
            System.out.println("No agents found. Run 'oryxos init' first.");
            return;
        }

        try (Stream<Path> dirs = Files.list(agentsDir)) {
            var agentNames =
                    dirs.filter(Files::isDirectory)
                            .map(p -> p.getFileName().toString())
                            .sorted()
                            .toList();

            if (agentNames.isEmpty()) {
                System.out.println(
                        "No agents found. Run 'oryxos profile create <NAME>' to create one.");
            } else {
                System.out.println("Available Profiles:");
                agentNames.forEach(name -> System.out.println("- " + name));
            }
        } catch (Exception e) {
            System.err.println("Error listing profiles: " + e.getMessage());
        }
    }
}
