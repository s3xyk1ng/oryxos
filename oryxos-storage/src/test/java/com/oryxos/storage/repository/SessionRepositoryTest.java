package com.oryxos.storage.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oryxos.storage.entity.SessionEntity;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/** SessionRepository 集成测试（@Tag("integration")）。 覆盖 1 个验收点：messages_json 持久化。 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:sqlite:file:test-sessions.db",
            "spring.datasource.driver-class-name=org.sqlite.JDBC",
            "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration/sqlite",
            "spring.flyway.baseline-on-migrate=true"
        })
@Tag("integration")
@DisplayName("SessionRepository 集成测试")
class SessionRepositoryTest {

    @Autowired private SessionRepository sessionRepository;

    @Test
    @DisplayName("messages_json 持久化：保存 Session 后重新读取，messages_json 不变")
    void messagesJsonPersistence() throws IOException {
        // Given: 一条包含对话历史的 Session
        String sessionId = "test-session-001";
        String messagesJson =
                "[{\"role\":\"user\",\"content\":\"hello\"},{\"role\":\"assistant\",\"content\":\"hi\"}]";
        Instant now = Instant.now();

        SessionEntity entity = new SessionEntity();
        entity.setSessionId(sessionId);
        entity.setProfileName("weather");
        entity.setChannel("cli");
        entity.setUserId("cli-user");
        entity.setMessagesJson(messagesJson);
        entity.setStatus("active");
        entity.setCreatedAt(now);
        entity.setLastActiveAt(now);

        // When: 保存到数据库
        sessionRepository.save(entity);
        sessionRepository.flush();

        // Then: 重新读取，messages_json 不变
        assertTrue(sessionRepository.existsById(sessionId));
        SessionEntity loaded = sessionRepository.findById(sessionId).orElse(null);
        assertEquals(messagesJson, loaded.getMessagesJson());
        assertEquals("weather", loaded.getProfileName());
        assertEquals("cli", loaded.getChannel());
        assertEquals("cli-user", loaded.getUserId());
        assertEquals("active", loaded.getStatus());
    }
}
