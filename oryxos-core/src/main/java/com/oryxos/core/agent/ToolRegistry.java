package com.oryxos.core.agent;

import com.oryxos.core.model.OryxTool;
import com.oryxos.core.model.Profile;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 工具注册表：启动时通过 Spring 容器扫描所有 OryxTool Bean， 提供 getTool(name) / getAllTools() /
 * getToolsForProfile(profile) 方法。
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, OryxTool> tools = new ConcurrentHashMap<>();

    /** 注册一个工具。 */
    public void register(OryxTool tool) {
        tools.put(tool.getName(), tool);
        log.debug("Registered tool: {}", tool.getName());
    }

    /** 按名称查找工具。 */
    public OryxTool getTool(String name) {
        return tools.get(name);
    }

    /** 获取所有已注册工具。 */
    public List<OryxTool> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    /** 获取指定 Profile 可用的工具列表。 如果 Profile 指定了 tools 白名单，只返回白名单中的工具；否则返回全部。 */
    public List<OryxTool> getToolsForProfile(Profile profile) {
        List<String> allowedTools = profile.getTools();
        if (allowedTools == null || allowedTools.isEmpty()) {
            return getAllTools();
        }
        return tools.values().stream()
                .filter(t -> allowedTools.contains(t.getName()))
                .collect(Collectors.toList());
    }

    /** 批量注册工具。 */
    public void registerAll(List<OryxTool> toolList) {
        toolList.forEach(this::register);
    }
}
