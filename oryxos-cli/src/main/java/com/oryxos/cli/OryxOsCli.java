package com.oryxos.cli;

import com.oryxos.boot.OryxOsApplication;
import com.oryxos.cli.command.ChatCommand;
import com.oryxos.cli.command.GatewayCommand;
import com.oryxos.cli.command.InitCommand;
import com.oryxos.cli.command.ProfileCommand;
import com.oryxos.cli.command.ProviderCommand;
import com.oryxos.cli.command.ServeCommand;
import com.oryxos.cli.command.StatusCommand;
import com.oryxos.cli.command.ToolCommand;
import java.util.Set;
import java.util.concurrent.Callable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IFactory;

@Command(
        name = "oryxos",
        mixinStandardHelpOptions = true,
        version = "OryxOS 1.0.0-SNAPSHOT",
        description = "Java-native Enterprise Agent OS",
        subcommands = {
            InitCommand.class,
            ChatCommand.class,
            ServeCommand.class,
            GatewayCommand.class,
            StatusCommand.class,
            ProfileCommand.class,
            ProviderCommand.class,
            ToolCommand.class
        })
public class OryxOsCli implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println(
                """

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

    /** 轻命令白名单（不启动 Spring）。 */
    private static final Set<String> LIGHT_COMMANDS =
            Set.of("init", "status", "profile", "provider", "tool");

    public static void main(String[] args) {
        // 判断是否为轻命令
        if (args.length > 0 && LIGHT_COMMANDS.contains(args[0])) {
            // 轻命令：不启动 Spring，直接执行
            runLightCommand(args);
            return;
        }

        // 重命令：启动 Spring
        runHeavyCommand(args);
    }

    /** 执行轻命令（不启动 Spring）。 */
    private static void runLightCommand(String[] args) {
        int exitCode = new CommandLine(new OryxOsCli()).execute(args);
        System.exit(exitCode);
    }

    /** 执行重命令（启动 Spring）。 */
    private static void runHeavyCommand(String[] args) {
        // 判断是否需要 Web 服务器
        boolean needsWeb = args.length > 0 && "serve".equals(args[0]);

        SpringApplication app = new SpringApplication(OryxOsApplication.class);
        app.setWebApplicationType(needsWeb ? WebApplicationType.SERVLET : WebApplicationType.NONE);

        try (ConfigurableApplicationContext ctx = app.run(args)) {
            // 执行启动验证
            ctx.getBean(StartupVerifier.class).verify();

            // 执行 Picocli 命令
            IFactory factory = new picocli.spring.PicocliSpringFactory(ctx);
            int exitCode = new CommandLine(new OryxOsCli(), factory).execute(args);

            // 对于 serve 命令，不退出（保持运行）
            if (needsWeb) {
                return;
            }

            System.exit(exitCode);
        }
    }
}
