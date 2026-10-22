package com.oryxos.cli.command.subcommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Command;

/** provider list 子命令（轻命令，不启动 Spring）。 读取 .oryxos/config/application.yml 并列出已配置的 Provider。 */
@Command(name = "list", description = "List configured Providers")
public class ProviderListCommand implements Runnable {

    @Override
    public void run() {
        Path configFile =
                Path.of(System.getProperty("user.dir"), ".oryxos", "config", "application.yml");

        if (!Files.exists(configFile)) {
            System.out.println("No providers configured. Edit .oryxos/config/application.yml");
            return;
        }

        try {
            String content = Files.readString(configFile);
            // 简单解析：查找 providers 下的键名
            if (content.contains("providers:")) {
                System.out.println("Configured Providers:");
                String[] lines = content.split("\n");
                boolean inProviders = false;
                for (String line : lines) {
                    if (line.trim().startsWith("providers:")) {
                        inProviders = true;
                        continue;
                    }
                    if (inProviders && line.matches("\\s+\\w[\\w-]*:.*")) {
                        String providerName = line.trim().split(":")[0].trim();
                        System.out.println("- " + providerName);
                    }
                    if (inProviders && !line.startsWith(" ") && !line.trim().isEmpty()) {
                        break;
                    }
                }
            } else {
                System.out.println("No providers configured. Edit .oryxos/config/application.yml");
            }
        } catch (IOException e) {
            System.err.println("Error reading config: " + e.getMessage());
        }
    }
}
