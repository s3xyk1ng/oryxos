package com.oryxos.cli.command.subcommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import picocli.CommandLine.Command;

/** tool list 子命令（轻命令，不启动 Spring）。 扫描 .oryxos/tools/ 目录并列出可用 Tool。 */
@Command(name = "list", description = "List available Tools")
public class ToolListCommand implements Runnable {

    @Override
    public void run() {
        Path toolsDir = Path.of(System.getProperty("user.dir"), ".oryxos", "tools");

        if (!Files.exists(toolsDir)) {
            System.out.println("No tools found.");
            return;
        }

        try (Stream<Path> dirs = Files.list(toolsDir)) {
            var toolNames =
                    dirs.filter(Files::isDirectory)
                            .map(p -> p.getFileName().toString())
                            .sorted()
                            .toList();

            if (toolNames.isEmpty()) {
                System.out.println("No tools found.");
            } else {
                System.out.println("Available Tools:");
                toolNames.forEach(name -> System.out.println("- " + name));
            }
        } catch (IOException e) {
            System.err.println("Error listing tools: " + e.getMessage());
        }
    }
}
