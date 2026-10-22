package com.oryxos.cli.command;

import com.oryxos.cli.command.subcommand.ProviderListCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Provider 命令组（轻命令，不启动 Spring）。 下挂 list 子命令。 */
@Command(
        name = "provider",
        description = "Manage LLM Providers",
        subcommands = {ProviderListCommand.class})
public class ProviderCommand implements Runnable {

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
}
