package com.oryxos.core.agent;

import com.oryxos.core.model.*;
import com.oryxos.core.react.LlmPort;
import com.oryxos.core.react.ReActLoop;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Agent 服务：统一入口。 process(Session, message) → 调 ReActLoop.run → 返回回复。 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final ReActLoop reActLoop;
    private final SessionManager sessionManager;
    private final ToolRegistry toolRegistry;
    private final LlmPortFactory llmPortFactory;

    public AgentService(
            ReActLoop reActLoop,
            SessionManager sessionManager,
            ToolRegistry toolRegistry,
            LlmPortFactory llmPortFactory) {
        this.reActLoop = reActLoop;
        this.sessionManager = sessionManager;
        this.toolRegistry = toolRegistry;
        this.llmPortFactory = llmPortFactory;
    }

    /** 处理用户消息，返回 Agent 回复。 */
    public String process(Session session, String message) {
        Profile profile = sessionManager.getProfile(session);
        if (profile == null) {
            throw new IllegalArgumentException(
                    "Profile not found for session: " + session.getSessionId());
        }

        LlmPort llm = llmPortFactory.createForProfile(profile);
        List<OryxTool> tools = toolRegistry.getToolsForProfile(profile);

        return reActLoop.run(session, llm, profile, tools, message);
    }
}
