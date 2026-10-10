package com.oryxos.core.agent;

import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session 管理器（内存版）。
 * 按 Channel + user + profile 联合 ID 管理，支持创建/查询/追加消息/截断历史。
 * US-5 升级为 SQLite 持久化。
 */
@Component
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Profile> sessionProfiles = new ConcurrentHashMap<>();
    private final ProfileRegistry profileRegistry;

    public SessionManager(ProfileRegistry profileRegistry) {
        this.profileRegistry = profileRegistry;
    }

    /**
     * 获取或创建 Session。
     */
    public Session getOrCreateSession(String channel, String userId, String profileName) {
        String sessionId = generateSessionId(channel, userId, profileName);
        return sessions.computeIfAbsent(sessionId, id -> {
            Session session = new Session(id, profileName, channel, userId);
            sessionProfiles.put(id, profileRegistry.getByName(profileName));
            log.info("Created session: {} (channel={}, user={}, profile={})",
                    id, channel, userId, profileName);
            return session;
        });
    }

    /**
     * 按 ID 查询 Session。
     */
    public Session getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 获取 Session 关联的 Profile。
     */
    public Profile getProfile(Session session) {
        return sessionProfiles.get(session.getSessionId());
    }

    /**
     * 归档 Session。
     */
    public void archiveSession(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session != null) {
            session.archive();
        }
    }

    /**
     * 生成 Session ID：sha256(channel + ":" + userId + ":" + profileName) 取前 64 字符。
     */
    public static String generateSessionId(String channel, String userId, String profileName) {
        try {
            String input = channel + ":" + userId + ":" + profileName;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 64);
        } catch (Exception e) {
            return channel + "-" + userId + "-" + profileName;
        }
    }
}
