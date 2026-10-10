package com.oryxos.core.agent;

import com.oryxos.core.context.ConfigLoader;
import com.oryxos.core.model.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Agent 加载器：扫描 .oryxos/agents/ 目录，解析 AGENT.md frontmatter 生成 Profile，
 * 注册到 ProfileRegistry。
 */
@Component
public class AgentLoader {

    private static final Logger log = LoggerFactory.getLogger(AgentLoader.class);
    private final ProfileRegistry profileRegistry;
    private final ConfigLoader configLoader;

    public AgentLoader(ProfileRegistry profileRegistry, ConfigLoader configLoader) {
        this.profileRegistry = profileRegistry;
        this.configLoader = configLoader;
    }

    /**
     * 扫描 agents 目录，加载所有 Agent 的 Profile。
     *
     * @param workspaceRoot 工作区根目录（包含 .oryxos/）
     */
    public void loadAgents(Path workspaceRoot) {
        Path agentsDir = workspaceRoot.resolve(".oryxos").resolve("agents");
        if (!Files.isDirectory(agentsDir)) {
            log.warn("Agents directory not found: {}", agentsDir);
            return;
        }

        try (Stream<Path> dirs = Files.list(agentsDir)) {
            dirs.filter(Files::isDirectory).forEach(agentDir -> {
                try {
                    loadAgent(agentDir);
                } catch (Exception e) {
                    log.error("Failed to load agent from {}: {}", agentDir, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.error("Failed to scan agents directory: {}", e.getMessage());
        }

        log.info("Loaded {} profiles", profileRegistry.size());
    }

    private void loadAgent(Path agentDir) throws IOException {
        Path agentMd = agentDir.resolve("AGENT.md");
        if (!Files.exists(agentMd)) {
            log.debug("No AGENT.md in {}, skipping", agentDir);
            return;
        }

        String content = Files.readString(agentMd);
        Map<String, Object> frontmatter = parseFrontmatter(content);
        if (frontmatter == null || !frontmatter.containsKey("name")) {
            log.warn("AGENT.md in {} missing frontmatter or name, skipping", agentDir);
            return;
        }

        Profile profile = deriveProfile(frontmatter);
        profileRegistry.register(profile);
        log.info("Loaded agent: {} from {}", profile.getName(), agentDir);
    }

    /**
     * 从 AGENT.md frontmatter 派生 Profile。
     */
    public Profile deriveProfile(Map<String, Object> frontmatter) {
        Profile profile = new Profile();
        profile.setName((String) frontmatter.get("name"));
        profile.setDescription((String) frontmatter.get("description"));
        profile.setProvider((String) frontmatter.get("provider"));

        // tools
        Object toolsObj = frontmatter.get("tools");
        if (toolsObj instanceof List<?> toolsList) {
            profile.setTools(toolsList.stream().map(Object::toString).toList());
        }

        // channels
        Object channelsObj = frontmatter.get("channels");
        if (channelsObj instanceof List<?> channelsList) {
            profile.setChannels(channelsList.stream().map(Object::toString).toList());
        }

        // max_iterations
        Object maxIter = frontmatter.get("max_iterations");
        if (maxIter instanceof Integer intVal) {
            profile.setMaxIterations(intVal);
        }

        // max_history_turns
        Object maxTurns = frontmatter.get("max_history_turns");
        if (maxTurns instanceof Integer intVal) {
            profile.setMaxHistoryTurns(intVal);
        }

        // mcp_servers
        Object mcpObj = frontmatter.get("mcp_servers");
        if (mcpObj instanceof Map<?, ?> mcpMap) {
            Map<String, String> mcpServers = new HashMap<>();
            mcpMap.forEach((k, v) -> mcpServers.put(k.toString(), v.toString()));
            profile.setMcpServers(mcpServers);
        }

        // schedules
        Object schedObj = frontmatter.get("schedules");
        if (schedObj instanceof List<?> schedList) {
            List<Profile.ScheduleConfig> schedules = new ArrayList<>();
            for (Object item : schedList) {
                if (item instanceof Map<?, ?> map) {
                    Profile.ScheduleConfig sc = new Profile.ScheduleConfig();
                    sc.setKey((String) map.get("key"));
                    sc.setCron((String) map.get("cron"));
                    Object zoneObj = map.get("zone");
                    sc.setZone(zoneObj != null ? zoneObj.toString() : "Asia/Shanghai");
                    sc.setMessage((String) map.get("message"));
                    sc.setDisplayName((String) map.get("display_name"));
                    schedules.add(sc);
                }
            }
            profile.setSchedules(schedules);
        }

        return profile;
    }

    /**
     * 解析 Markdown frontmatter（--- 包裹的 YAML）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFrontmatter(String content) {
        if (!content.startsWith("---")) return null;
        int endIndex = content.indexOf("---", 3);
        if (endIndex < 0) return null;
        String yaml = content.substring(3, endIndex).trim();
        Yaml parser = new Yaml();
        return parser.load(yaml);
    }
}
