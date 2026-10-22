package com.oryxos.cli.command;

import com.oryxos.cli.command.subcommand.ToolListCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Tool 命令组（轻命令，不启动 Spring）。 下挂 list 子命令。 */
@Command(
        name = "tool",
        description = "Manage Tools",
        subcommands = {ToolListCommand.class})
public class ToolCommand implements Runnable {

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
}
