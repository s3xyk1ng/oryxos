package com.oryxos.cli;

import com.oryxos.cli.command.ChatCommand;
import com.oryxos.cli.command.InitCommand;
import com.oryxos.cli.command.ServeCommand;
import com.oryxos.cli.command.StatusCommand;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IFactory;

import com.oryxos.boot.OryxOsApplication;

import java.util.concurrent.Callable;

@Command(name = "oryxos",
        mixinStandardHelpOptions = true,
        version = "OryxOS 1.0.0-SNAPSHOT",
        description = "Java-native Enterprise Agent OS",
        subcommands = {InitCommand.class, ChatCommand.class, ServeCommand.class, StatusCommand.class})
public class OryxOsCli implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("""
                
                  ___  __  __   __
                 / _ \\/ / / /\\ / /
                / , _/ /_/ // // /
               /_/|_|\\__, / \\_,_/
                      /____/
                 Java-native Enterprise Agent OS v1.0.0
                """);
        new CommandLine(this).usage(System.out);
        return 0;
    }

    public static void main(String[] args) {
        // Check if a long-running subcommand is requested
        boolean isLongRunning = args.length > 0
                && ("serve".equals(args[0]) || "gateway".equals(args[0]));

        if (isLongRunning) {
            // For serve/gateway: start Spring Boot with web server
            SpringApplication app = new SpringApplication(OryxOsApplication.class);
            app.setWebApplicationType(WebApplicationType.SERVLET);
            app.run(args);
            return;
        }

        // For short commands: start Spring (non-web), wire Picocli, execute, exit
        SpringApplication app = new SpringApplication(OryxOsApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext ctx = app.run(args)) {
            IFactory factory = new picocli.spring.PicocliSpringFactory(ctx);
            int exitCode = new CommandLine(new OryxOsCli(), factory).execute(args);
            System.exit(exitCode);
        }
    }
}
