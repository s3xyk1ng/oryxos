package com.oryxos.cli.command;

import com.oryxos.core.agent.ProfileRegistry;
import com.oryxos.core.agent.ToolRegistry;
import com.oryxos.core.model.OryxTool;
import com.oryxos.core.model.Profile;
import picocli.CommandLine.Command;

/** oryxos status：显示系统状态概览。 */
@Command(name = "status", description = "Show OryxOS system status")
public class StatusCommand implements Runnable {

    private final ProfileRegistry profileRegistry;
    private final ToolRegistry toolRegistry;

    public StatusCommand(ProfileRegistry profileRegistry, ToolRegistry toolRegistry) {
        this.profileRegistry = profileRegistry;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public void run() {
        System.out.println("OryxOS Status");
        System.out.println("=============");
        System.out.println("Profiles: " + profileRegistry.size());
        for (Profile p : profileRegistry.getAll()) {
            System.out.println("  - " + p.getName() + " (provider: " + p.getProvider() + ")");
        }
        System.out.println("Tools: " + toolRegistry.getAllTools().size());
        for (OryxTool t : toolRegistry.getAllTools()) {
            System.out.println("  - " + t.getName());
        }
        System.out.println("Java: " + System.getProperty("java.version"));
        System.out.println("OS: " + System.getProperty("os.name"));
        System.out.println(
                "Memory: "
                        + Runtime.getRuntime().freeMemory() / 1024 / 1024
                        + "MB free / "
                        + Runtime.getRuntime().maxMemory() / 1024 / 1024
                        + "MB max");
    }
}
