package com.oryxos.cli.command;

import com.oryxos.cli.command.subcommand.ProfileCreateCommand;
import com.oryxos.cli.command.subcommand.ProfileDeleteCommand;
import com.oryxos.cli.command.subcommand.ProfileListCommand;
import com.oryxos.cli.command.subcommand.ProfileShowCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Profile 命令组。 下挂 list/create/show/delete 四个子命令。 */
@Command(
        name = "profile",
        description = "Manage Agent Profiles",
        subcommands = {
            ProfileListCommand.class,
            ProfileCreateCommand.class,
            ProfileShowCommand.class,
            ProfileDeleteCommand.class
        })
public class ProfileCommand implements Runnable {

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
}
