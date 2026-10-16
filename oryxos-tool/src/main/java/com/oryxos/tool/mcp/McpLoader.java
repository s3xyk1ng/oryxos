package com.oryxos.tool.mcp;

import com.oryxos.core.agent.ProfileRegistry;
import com.oryxos.core.agent.ToolRegistry;
import com.oryxos.core.model.Profile;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * MCP 加载器：Agent 加载完成后，解析每个 Profile 的 mcp_servers 配置， 触发 McpClientService 连接对应 MCP Server，将 MCP
 * 工具注册到 ToolRegistry。
 */
@Component
public class McpLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(McpLoader.class);

    private final McpClientService mcpClientService;
    private final ProfileRegistry profileRegistry;
    private final ToolRegistry toolRegistry;

    public McpLoader(
            McpClientService mcpClientService,
            ProfileRegistry profileRegistry,
            ToolRegistry toolRegistry) {
        this.mcpClientService = mcpClientService;
        this.profileRegistry = profileRegistry;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Profile profile : profileRegistry.getAll()) {
            Map<String, String> mcpServers = profile.getMcpServers();
            if (mcpServers == null || mcpServers.isEmpty()) {
                continue;
            }
            log.info(
                    "Loading MCP servers for profile '{}': {}",
                    profile.getName(),
                    mcpServers.keySet());

            for (Map.Entry<String, String> entry : mcpServers.entrySet()) {
                String serverName = entry.getKey();
                String config = entry.getValue();

                try {
                    // Parse config: "command arg1 arg2" for stdio, or "sse:url" for SSE
                    if (config.startsWith("sse:")) {
                        String url = config.substring(4);
                        mcpClientService.connect(serverName, "sse", null, null, url);
                    } else {
                        // stdio: "command arg1 arg2 ..."
                        String[] parts = config.split("\\s+");
                        String command = parts[0];
                        List<String> cmdArgs =
                                parts.length > 1
                                        ? List.of(
                                                java.util.Arrays.copyOfRange(
                                                        parts, 1, parts.length))
                                        : List.of();
                        mcpClientService.connect(serverName, "stdio", command, cmdArgs, null);
                    }

                    // List tools from this MCP server and register them
                    List<McpClientService.McpToolInfo> mcpTools =
                            mcpClientService.listTools(serverName);
                    for (McpClientService.McpToolInfo toolInfo : mcpTools) {
                        McpToolAdapter adapter =
                                new McpToolAdapter(serverName, toolInfo, mcpClientService);
                        toolRegistry.register(adapter);
                        log.info(
                                "Registered MCP tool: {} from server {}",
                                adapter.getName(),
                                serverName);
                    }
                } catch (Exception e) {
                    log.error("Failed to load MCP server '{}': {}", serverName, e.getMessage());
                }
            }
        }
    }
}
