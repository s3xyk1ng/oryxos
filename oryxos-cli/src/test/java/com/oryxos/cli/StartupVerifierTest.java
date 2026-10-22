package com.oryxos.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oryxos.core.agent.AgentService;
import com.oryxos.core.agent.SessionManager;
import com.oryxos.storage.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

/** StartupVerifier 单元测试。 覆盖三步验证：Bean 断言 + 数据访问层读写验证。 */
@DisplayName("StartupVerifier 测试")
class StartupVerifierTest {

    private ApplicationContext applicationContext;
    private SessionRepository sessionRepository;
    private StartupVerifier startupVerifier;

    @BeforeEach
    void setUp() {
        applicationContext = mock(ApplicationContext.class);
        sessionRepository = mock(SessionRepository.class);
        startupVerifier = new StartupVerifier(applicationContext, sessionRepository);
    }

    @Test
    @DisplayName("三步验证全部通过：Bean 存在 + 数据访问层可达")
    void verifyPassesWhenAllBeansExistAndDataAccessWorks() {
        // Given: 关键 Bean 存在
        when(applicationContext.getBean(AgentService.class)).thenReturn(mock(AgentService.class));
        when(applicationContext.getBean(SessionManager.class))
                .thenReturn(mock(SessionManager.class));
        when(sessionRepository.count()).thenReturn(0L);

        // Then: 验证不抛异常
        assertDoesNotThrow(() -> startupVerifier.verify());
    }

    @Test
    @DisplayName("Bean 缺失时抛异常：AgentService 不存在")
    void verifyThrowsWhenBeanMissing() {
        // Given: AgentService 缺失
        when(applicationContext.getBean(AgentService.class))
                .thenThrow(
                        new org.springframework.beans.factory.NoSuchBeanDefinitionException(
                                AgentService.class));

        // Then: 验证抛异常
        assertThrows(RuntimeException.class, () -> startupVerifier.verify());
    }

    @Test
    @DisplayName("数据访问层不可达时抛异常：SQLite 连接失败")
    void verifyThrowsWhenDataAccessFails() {
        // Given: Bean 存在但数据访问层失败
        when(applicationContext.getBean(AgentService.class)).thenReturn(mock(AgentService.class));
        when(applicationContext.getBean(SessionManager.class))
                .thenReturn(mock(SessionManager.class));
        when(sessionRepository.count()).thenThrow(new RuntimeException("SQLite connection lost"));

        // Then: 验证抛异常
        assertThrows(RuntimeException.class, () -> startupVerifier.verify());
    }
}
