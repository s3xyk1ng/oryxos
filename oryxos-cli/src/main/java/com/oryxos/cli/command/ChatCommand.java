package com.oryxos.cli.command;

import com.oryxos.core.agent.AgentLoader;
import com.oryxos.core.agent.AgentService;
import com.oryxos.core.agent.ProfileRegistry;
import com.oryxos.core.agent.SessionManager;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import java.nio.file.Path;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * oryxos chat：交互式多轮对话，完整 ReAct 循环。 读取用户输入 → 调 AgentService.process → 打印响应 → 支持 /quit 退出。
 * 核心阶段不询问用户名，直接使用 "cli-user"。
 */
@Command(name = "chat", description = "Start interactive chat with an Agent")
public class ChatCommand implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ChatCommand.class);

    // 依赖注入（重命令通过 Spring 注入）
    private final AgentService agentService;
    private final SessionManager sessionManager;
    private final ProfileRegistry profileRegistry;
    private final AgentLoader agentLoader;

    public ChatCommand(
            AgentService agentService,
            SessionManager sessionManager,
            ProfileRegistry profileRegistry,
            AgentLoader agentLoader) {
        this.agentService = agentService;
        this.sessionManager = sessionManager;
        this.profileRegistry = profileRegistry;
        this.agentLoader = agentLoader;
    }

    @Parameters(paramLabel = "AGENT", description = "Agent name", defaultValue = "")
    private String agentName;

    @Override
    public void run() {
        // 加载 Agent
        agentLoader.loadAgents(Path.of(System.getProperty("user.dir")));

        if (profileRegistry.size() == 0) {
            System.err.println("No agents found. Run 'oryxos init' first.");
            return;
        }

        // 解析 Profile
        Profile profile = resolveProfile();

        // 创建 Session（默认用户身份 "cli-user"）
        Session session = sessionManager.getOrCreateSession("cli", "cli-user", profile.getName());

        System.out.println(
                "\nChat with " + profile.getName() + " (provider: " + profile.getProvider() + ")");
        System.out.println("Type /quit to exit\n");

        // 交互循环
        Scanner scanner = new Scanner(System.in);
        System.out.print("> ");
        while (scanner.hasNextLine()) {
            String input = scanner.nextLine().trim();

            // 空输入跳过
            if (input.isEmpty()) {
                System.out.print("> ");
                continue;
            }

            // /quit 退出
            if ("/quit".equals(input)) {
                System.out.println("Bye!");
                break;
            }

            // 调用 AgentService
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

    /** 解析用户指定的 Profile，若未指定或不存在则使用第一个。 */
    private Profile resolveProfile() {
        if (agentName != null && !agentName.isBlank() && profileRegistry.exists(agentName)) {
            return profileRegistry.getByName(agentName);
        }
        Profile profile = profileRegistry.getAll().iterator().next();
        System.out.println("Using default agent: " + profile.getName());
        return profile;
    }
}
