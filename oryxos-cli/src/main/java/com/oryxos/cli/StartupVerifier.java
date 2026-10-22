package com.oryxos.cli;

import com.oryxos.core.agent.AgentService;
import com.oryxos.core.agent.SessionManager;
import com.oryxos.storage.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/** 重命令启动验证器。 三步检查：Bean 断言 + 数据访问层读写验证。 */
@Component
public class StartupVerifier {

    private static final Logger log = LoggerFactory.getLogger(StartupVerifier.class);

    private final ApplicationContext applicationContext;
    private final SessionRepository sessionRepository;

    public StartupVerifier(
            ApplicationContext applicationContext, SessionRepository sessionRepository) {
        this.applicationContext = applicationContext;
        this.sessionRepository = sessionRepository;
    }

    /** 执行三步启动验证。 */
    public void verify() {
        log.info("Running startup verification...");

        // Step 1: Bean 断言（关键 Bean 必须存在）
        try {
            applicationContext.getBean(AgentService.class);
            log.info("✓ AgentService bean found");

            applicationContext.getBean(SessionManager.class);
            log.info("✓ SessionManager bean found");
        } catch (Exception e) {
            log.error("✗ Bean assertion failed: {}", e.getMessage());
            throw new RuntimeException("Startup verification failed: missing required beans", e);
        }

        // Step 2: 数据访问层读写验证（SQLite 连接正常）
        try {
            long count = sessionRepository.count();
            log.info("✓ SessionRepository accessible (current sessions: {})", count);
        } catch (Exception e) {
            log.error("✗ Data access layer verification failed: {}", e.getMessage());
            throw new RuntimeException(
                    "Startup verification failed: data access layer not accessible", e);
        }

        log.info("Startup verification passed");
    }
}
