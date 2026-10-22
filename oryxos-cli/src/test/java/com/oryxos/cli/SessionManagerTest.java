package com.oryxos.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oryxos.core.agent.ProfileRegistry;
import com.oryxos.core.agent.SessionManager;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SessionManager 单元测试（内存版）。 覆盖 2 个验收点：幂等性、三元组唯一性。 */
@DisplayName("SessionManager 测试")
class SessionManagerTest {

    private SessionManager sessionManager;
    private ProfileRegistry profileRegistry;

    @BeforeEach
    void setUp() {
        profileRegistry = mock(ProfileRegistry.class);
        // Stub getByName to return a mock Profile (ConcurrentHashMap doesn't allow null values)
        Profile mockProfile = new Profile("test");
        mockProfile.setProvider("deepseek");
        mockProfile.setModel("deepseek-chat");
        when(profileRegistry.getByName(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(mockProfile);
        sessionManager = new SessionManager(profileRegistry);
    }

    @Test
    @DisplayName("幂等性：同一三元组两次 getOrCreate 返回同一个 Session")
    void idempotentSameTripleReturnsSameSession() {
        // Given: 同一三元组
        String channel = "cli";
        String userId = "cli-user";
        String profileName = "weather";

        // When: 两次调用 getOrCreateSession
        Session session1 = sessionManager.getOrCreateSession(channel, userId, profileName);
        Session session2 = sessionManager.getOrCreateSession(channel, userId, profileName);

        // Then: 返回同一个 Session（幂等）
        assertNotNull(session1);
        assertNotNull(session2);
        assertEquals(session1.getSessionId(), session2.getSessionId());
        assertEquals(session1, session2);
    }

    @Test
    @DisplayName("三元组唯一性：channel/user/profile 任一不同就是不同 Session")
    void differentTripleReturnsDifferentSession() {
        // Given: 三个不同的三元组
        Session session1 = sessionManager.getOrCreateSession("cli", "user1", "weather");
        Session session2 = sessionManager.getOrCreateSession("web", "user1", "weather");
        Session session3 = sessionManager.getOrCreateSession("cli", "user2", "weather");
        Session session4 = sessionManager.getOrCreateSession("cli", "user1", "translator");

        // Then: 所有 Session ID 都不同
        assertNotEquals(session1.getSessionId(), session2.getSessionId());
        assertNotEquals(session1.getSessionId(), session3.getSessionId());
        assertNotEquals(session1.getSessionId(), session4.getSessionId());
        assertNotEquals(session2.getSessionId(), session3.getSessionId());
        assertNotEquals(session2.getSessionId(), session4.getSessionId());
        assertNotEquals(session3.getSessionId(), session4.getSessionId());
    }
}
