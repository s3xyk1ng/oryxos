package com.oryxos.core.model;

import java.util.List;
import java.util.Map;

/**
 * Agent 运行时配置，由 AGENT.md frontmatter 派生。
 * 决定 Agent "怎么跑"：绑定的 LLM Provider、可用 Tool 列表、Channel、定时规则。
 */
public class Profile {

    private String name;
    private String description;
    private String provider;
    private List<String> tools;
    private List<String> channels;
    private List<ScheduleConfig> schedules;
    private Map<String, String> mcpServers;
    private String defaultNotifyChannel;
    private int maxIterations = 10;
    private int maxHistoryTurns = 20;

    public Profile() {}

    public Profile(String name) {
        this.name = name;
    }

    // --- Getters & Setters ---

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public List<String> getTools() { return tools; }
    public void setTools(List<String> tools) { this.tools = tools; }

    public List<String> getChannels() { return channels; }
    public void setChannels(List<String> channels) { this.channels = channels; }

    public List<ScheduleConfig> getSchedules() { return schedules; }
    public void setSchedules(List<ScheduleConfig> schedules) { this.schedules = schedules; }

    public Map<String, String> getMcpServers() { return mcpServers; }
    public void setMcpServers(Map<String, String> mcpServers) { this.mcpServers = mcpServers; }

    public String getDefaultNotifyChannel() { return defaultNotifyChannel; }
    public void setDefaultNotifyChannel(String defaultNotifyChannel) { this.defaultNotifyChannel = defaultNotifyChannel; }

    public int getMaxIterations() { return maxIterations; }
    public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }

    public int getMaxHistoryTurns() { return maxHistoryTurns; }
    public void setMaxHistoryTurns(int maxHistoryTurns) { this.maxHistoryTurns = maxHistoryTurns; }

    /**
     * 定时任务配置（嵌套在 Profile 的 schedules 列表中）。
     */
    public static class ScheduleConfig {
        private String key;
        private String cron;
        private String zone = "Asia/Shanghai";
        private String message;
        private String displayName;

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }

        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }

        public String getZone() { return zone; }
        public void setZone(String zone) { this.zone = zone; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
    }
}
