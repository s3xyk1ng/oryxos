package com.oryxos.core.agent;

import com.oryxos.core.model.*;
import com.oryxos.core.react.ReActLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Agent 服务：统一入口。 process(Session, message) → ProfileContext.set → ReActLoop.run →
 * sessionManager.save → 返回回复，finally 中 ProfileContext.clear。
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final ReActLoop reActLoop;
    private final SessionManager sessionManager;

    public AgentService(ReActLoop reActLoop, SessionManager sessionManager) {
        this.reActLoop = reActLoop;
        this.sessionManager = sessionManager;
    }

    /** 处理用户消息，返回 Agent 回复。 */
    public String process(Session session, String message) {
        Profile profile = sessionManager.getProfile(session);
        if (profile == null) {
            throw new IllegalArgumentException(
                    "Profile not found for session: " + session.getSessionId());
        }

        ProfileContext.set(profile);
        try {
            ProviderResponse response = reActLoop.run(session, profile, message);
            sessionManager.save(session);
            return response.content();
        } catch (Exception e) {
            log.error(
                    "Agent process failed for session {}: {}",
                    session.getSessionId(),
                    e.getMessage());
            throw e;
        } finally {
            ProfileContext.clear();
        }
    }
}
