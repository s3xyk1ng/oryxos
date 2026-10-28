package com.oryxos.memory;

import com.oryxos.storage.repository.MemoryEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQLite 后端契约测试。
 */
@SpringBootTest(classes = MemoryTestApplication.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/memory-contract.db",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/sqlite",
        "oryxos.memory.backend=sqlite"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SqliteMemoryStoreContractTest extends LongTermMemoryStoreContractTest {

    @Autowired
    private MemoryEntryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Override
    protected LongTermMemoryStore createStore() {
        return new SqliteMemoryStore(repository, 200);
    }

    @Override
    protected LongTermMemoryStore createAnotherStore() {
        return new SqliteMemoryStore(repository, 200);
    }

    @Test
    void load_ordersCoreByStableIdAscending() {
        SqliteMemoryStore store = (SqliteMemoryStore) createStore();
        store.append("first core", MemoryScope.CORE);
        store.append("second core", MemoryScope.CORE);

        MemoryContent loaded = store.load();
        assertThat(loaded.coreSection()).containsSubsequence("first core", "second core");
    }

    @Test
    void recall_usesCaseInsensitiveKeywordMatching() {
        SqliteMemoryStore store = (SqliteMemoryStore) createStore();
        store.append("Project ORYXOS roadmap", MemoryScope.ARCHIVAL);

        assertThat(store.recallByKeyword("oryxos"))
                .hasSize(1)
                .contains("Project ORYXOS roadmap");
    }

    @Test
    void migration_createsMemoryEntriesSchema() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'memory_entries'",
                Integer.class);
        Integer indexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'idx_memory_entries_scope_id'",
                Integer.class);

        assertThat(tableCount).isOne();
        assertThat(indexCount).isOne();
    }

    @Test
    void database_rejectsInvalidScopeAndBlankContent() {
        org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO memory_entries(scope, content) VALUES (?, ?)", "INVALID", "valid"));
        org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO memory_entries(scope, content) VALUES (?, ?)", "CORE", "   "));
    }
}
