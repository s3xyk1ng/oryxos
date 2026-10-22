package com.oryxos.cli.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import picocli.CommandLine.Command;

/** oryxos status：显示系统状态概览（轻命令，不启动 Spring）。 直接扫描 .oryxos/ 目录获取 Agent/Provider/Tool 数量。 */
@Command(name = "status", description = "Show OryxOS system status")
public class StatusCommand implements Runnable {

    @Override
    public void run() {
        Path workDir = Path.of(System.getProperty("user.dir"));
        Path oryxosDir = workDir.resolve(".oryxos");

        System.out.println("OryxOS Status");
        System.out.println("=============");
        System.out.println(
                "Workspace: " + (Files.exists(oryxosDir) ? ".oryxos/" : "NOT INITIALIZED"));

        // 统计 Agent 数量
        int agentCount = countSubdirectories(oryxosDir.resolve("agents"));
        System.out.println("Agents: " + agentCount);

        // 统计 Provider 数量（从配置文件读取）
        int providerCount = countProviders(oryxosDir.resolve("config").resolve("application.yml"));
        System.out.println("Providers: " + providerCount);

        // 统计 Tool 数量
        int toolCount = countSubdirectories(oryxosDir.resolve("tools"));
        System.out.println("Tools: " + toolCount);

        // 系统信息
        System.out.println("Java: " + System.getProperty("java.version"));
        System.out.println("OS: " + System.getProperty("os.name"));
    }

    /** 统计目录下的子目录数量。 */
    private int countSubdirectories(Path dir) {
        if (!Files.exists(dir)) {
            return 0;
        }
        try (Stream<Path> paths = Files.list(dir)) {
            return (int) paths.filter(Files::isDirectory).count();
        } catch (IOException e) {
            return 0;
        }
    }

    /** 从 application.yml 中统计 Provider 数量（简单行数统计）。 */
    private int countProviders(Path configFile) {
        if (!Files.exists(configFile)) {
            return 0;
        }
        try {
            String content = Files.readString(configFile);
            // 简单统计 "api-key" 出现次数作为 Provider 数量
            return content.split("api-key", -1).length - 1;
        } catch (IOException e) {
            return 0;
        }
    }
}
