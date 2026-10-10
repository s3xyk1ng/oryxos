package com.oryxos.cli.command;

import com.oryxos.core.agent.AgentLoader;
import com.oryxos.core.agent.AgentService;
import com.oryxos.core.agent.ProfileRegistry;
import com.oryxos.core.agent.SessionManager;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * oryxos chat：交互式多轮对话，完整 ReAct 循环。
 * 读取用户输入 → 调 AgentService.process → 打印响应 → 支持 /quit 退出。
 */
@Command(name = "chat", description = "Start interactive chat with an Agent")
public class ChatCommand implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ChatCommand.class);

    private final AgentService agentService;
    private final SessionManager sessionManager;
    private final ProfileRegistry profileRegistry;
    private final AgentLoader agentLoader;

    public ChatCommand(AgentService agentService, SessionManager sessionManager,
                       ProfileRegistry profileRegistry, AgentLoader agentLoader) {
        this.agentService = agentService;
        this.sessionManager = sessionManager;
        this.profileRegistry = profileRegistry;
        this.agentLoader = agentLoader;
    }

    @Parameters(paramLabel = "AGENT", description = "Agent name", defaultValue = "")
    private String agentName;

    @Override
    public void run() {
        // Load agents from workspace
        agentLoader.loadAgents(java.nio.file.Path.of("."));

        if (profileRegistry.size() == 0) {
            System.err.println("No agents found. Run 'oryxos init' first and create an Agent in .oryxos/agents/");
            return;
        }

        // Pick agent
        Profile profile;
        if (agentName != null && !agentName.isBlank() && profileRegistry.exists(agentName)) {
            profile = profileRegistry.getByName(agentName);
        } else {
            profile = profileRegistry.getAll().iterator().next();
            System.out.println("Using default agent: " + profile.getName());
        }

        // Prompt for user identity
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        System.out.print("Enter your name (or 'default'): ");
        String userId = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
        if (userId.isEmpty() || "default".equalsIgnoreCase(userId)) {
            userId = "cli-user";
        }

        // Create session
        Session session = sessionManager.getOrCreateSession("cli", userId, profile.getName());

        System.out.println("\nChat with " + profile.getName()
                + " (provider: " + profile.getProvider() + ")");
        System.out.println("Type /quit to exit, /reset to clear history\n");

        System.out.print("> ");
        while (scanner.hasNextLine()) {
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                System.out.print("> ");
                continue;
            }
            if ("/quit".equals(input) || "/exit".equals(input)) {
                System.out.println("Bye!");
                break;
            }
            if ("/reset".equals(input)) {
                session = sessionManager.getOrCreateSession("cli", userId, profile.getName());
                System.out.println("Session reset.\n");
                System.out.print("> ");
                continue;
            }

            try {
                String reply = agentService.process(session, input);
                System.out.println("\n" + reply + "\n");
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                log.error("Agent processing failed", e);
            }
            System.out.print("> ");
        }
        scanner.close();
    }
}
