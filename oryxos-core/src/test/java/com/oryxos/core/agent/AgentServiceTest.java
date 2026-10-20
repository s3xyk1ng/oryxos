package com.oryxos.core.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oryxos.core.model.*;
import com.oryxos.core.react.ReActLoop;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** AgentService 行为验证：正常流程、ProfileContext 生命周期、Session 持久化、异常清理、无泄漏。 */
@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock private ReActLoop reActLoop;
    @Mock private SessionManager sessionManager;

    private AgentService service;

    @BeforeEach
    void setUp() {
        service = new AgentService(reActLoop, sessionManager);
    }

    @Test
    @DisplayName("正常流程：process() 返回 LLM 响应内容")
    void normalFlow_returnsContent() {
        Profile profile = new Profile("agent");
        Session session = new Session("s1", "agent", "cli", "user");
        when(sessionManager.getProfile(session)).thenReturn(profile);
        when(reActLoop.run(session, profile, "hello"))
                .thenReturn(new ProviderResponse("Hi there!", Collections.emptyList(), null));

        String result = service.process(session, "hello");

        assertEquals("Hi there!", result);
        verify(reActLoop).run(session, profile, "hello");
    }

    @Test
    @DisplayName("ProfileContext set/clear：执行期间设置，执行后清理")
    void profileContext_setAndClear() {
        Profile profile = new Profile("agent");
        Session session = new Session("s1", "agent", "cli", "user");
        when(sessionManager.getProfile(session)).thenReturn(profile);
        when(reActLoop.run(session, profile, "hi"))
                .thenReturn(new ProviderResponse("ok", Collections.emptyList(), null));

        try (var mocked = mockStatic(ProfileContext.class)) {
            service.process(session, "hi");

            mocked.verify(() -> ProfileContext.set(profile));
            mocked.verify(() -> ProfileContext.clear());
        }
    }

    @Test
    @DisplayName("Session 持久化：process() 后 sessionManager.save() 被调用")
    void sessionPersisted_saveCalled() {
        Profile profile = new Profile("agent");
        Session session = new Session("s1", "agent", "cli", "user");
        when(sessionManager.getProfile(session)).thenReturn(profile);
        when(reActLoop.run(session, profile, "msg"))
                .thenReturn(new ProviderResponse("reply", Collections.emptyList(), null));

        service.process(session, "msg");

        verify(sessionManager).save(session);
    }

    @Test
    @DisplayName("Profile 不存在抛异常：getProfile 返回 null → IllegalArgumentException")
    void profileNotFound_throwsException() {
        Session session = new Session("s1", "agent", "cli", "user");
        when(sessionManager.getProfile(session)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.process(session, "hi"));

        verify(reActLoop, never()).run(any(), any(), anyString());
    }

    @Test
    @DisplayName("异常时清理：ReActLoop 抛异常 → ProfileContext 仍被清理")
    void exceptionDuringRun_profileContextCleared() {
        Profile profile = new Profile("agent");
        Session session = new Session("s1", "agent", "cli", "user");
        when(sessionManager.getProfile(session)).thenReturn(profile);
        when(reActLoop.run(session, profile, "hi")).thenThrow(new RuntimeException("LLM error"));

        try (var mocked = mockStatic(ProfileContext.class)) {
            assertThrows(RuntimeException.class, () -> service.process(session, "hi"));

            // finally 中 clear() 必须被调用
            mocked.verify(() -> ProfileContext.clear());
        }
    }

    @Test
    @DisplayName("回归：请求结束后 ProfileContext 无泄漏")
    void regression_noProfileContextLeak() {
        Profile profile = new Profile("agent");
        Session session = new Session("s1", "agent", "cli", "user");
        when(sessionManager.getProfile(session)).thenReturn(profile);
        when(reActLoop.run(session, profile, "test"))
                .thenReturn(new ProviderResponse("done", Collections.emptyList(), null));

        try (var mocked = mockStatic(ProfileContext.class)) {
            service.process(session, "test");

            // 验证 set 和 clear 都恰好调用了 1 次
            mocked.verify(() -> ProfileContext.set(profile), times(1));
            mocked.verify(() -> ProfileContext.clear(), times(1));
            // get() 返回 null（mock 默认行为），说明没有残留
            assertNull(ProfileContext.get());
        }
    }
}
