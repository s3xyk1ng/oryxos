package com.oryxos.tool;

import com.oryxos.core.agent.ToolRegistry;
import com.oryxos.core.model.ConditionalTool;
import com.oryxos.core.model.OryxTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动时自动将容器中所有 OryxTool Bean 注册到 ToolRegistry。
 */
@Component
public class ToolAutoRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ToolAutoRegistrar.class);

    private final ToolRegistry toolRegistry;
    private final List<OryxTool> tools;

    public ToolAutoRegistrar(ToolRegistry toolRegistry, List<OryxTool> tools) {
        this.toolRegistry = toolRegistry;
        this.tools = tools;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<OryxTool> available = new ArrayList<>();
        for (OryxTool tool : tools) {
            if (tool instanceof ConditionalTool conditional) {
                if (conditional.isAvailable()) {
                    available.add(tool);
                    log.info("Conditional tool '{}' is available, registering", tool.getName());
                } else {
                    log.info("Conditional tool '{}' is NOT available, skipping", tool.getName());
                }
            } else {
                available.add(tool);
            }
        }
        toolRegistry.registerAll(available);
        log.info("Auto-registered {} tools: {}", available.size(),
                available.stream().map(OryxTool::getName).toList());
    }
}
