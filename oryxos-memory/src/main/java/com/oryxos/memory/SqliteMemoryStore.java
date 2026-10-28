package com.oryxos.memory;

import com.oryxos.storage.entity.MemoryEntryEntity;
import com.oryxos.storage.repository.MemoryEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * 基于 SQLite 的长期记忆存储。
 * 通过 Spring Data JPA 操作 memory_entries 表，遵守 LongTermMemoryStore 契约。
 */
public class SqliteMemoryStore implements LongTermMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(SqliteMemoryStore.class);

    private final MemoryEntryRepository repository;
    private final int archivalLimit;

    public SqliteMemoryStore(MemoryEntryRepository repository, int archivalLimit) {
        this.repository = repository;
        this.archivalLimit = archivalLimit;
    }

    @Override
    public void append(String content, MemoryScope scope) {
        validate(content, scope);
        long start = System.nanoTime();
        try {
            MemoryEntryEntity entry = new MemoryEntryEntity();
            entry.setScope(scope.name());
            entry.setContent(content);
            entry.setCreatedAt(Instant.now());
            repository.save(entry);
            log.info("Memory append completed: backend=sqlite, scope={}, resultCount=1, durationMs={}",
                    scope, elapsedMillis(start));
        } catch (RuntimeException e) {
            log.error("Memory backend failure: backend=sqlite, operation=append, errorType={}",
                    e.getClass().getSimpleName());
            throw e;
        }
    }

    @Override
    public MemoryContent load() {
        try {
            List<MemoryEntryEntity> coreEntries = repository.findByScopeOrderByIdAsc(MemoryScope.CORE.name());
            List<MemoryEntryEntity> archivalEntries = repository.findLatestByScope(
                    MemoryScope.ARCHIVAL.name(), archivalLimit);

            String coreSection = joinContents(coreEntries);
            String archivalSection = joinContents(archivalEntries);
            return new MemoryContent(coreSection, archivalSection);
        } catch (RuntimeException e) {
            log.error("Memory backend failure: backend=sqlite, operation=load, errorType={}",
                    e.getClass().getSimpleName());
            throw e;
        }
    }

    @Override
    public List<String> recallByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("Keyword must not be blank");
        }
        long start = System.nanoTime();
        try {
            List<String> results = repository.findByScopeAndContentContains(MemoryScope.ARCHIVAL.name(), keyword)
                    .stream()
                    .map(MemoryEntryEntity::getContent)
                    .toList();
            log.info("Memory recall completed: backend=sqlite, scope=ARCHIVAL, resultCount={}, durationMs={}",
                    results.size(), elapsedMillis(start));
            return results;
        } catch (RuntimeException e) {
            log.error("Memory backend failure: backend=sqlite, operation=recall, errorType={}",
                    e.getClass().getSimpleName());
            throw e;
        }
    }

    private void validate(String content, MemoryScope scope) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Memory content must not be blank");
        }
        if (scope == null) {
            throw new IllegalArgumentException("Memory scope must not be null");
        }
    }

    private String joinContents(List<MemoryEntryEntity> entries) {
        if (entries.isEmpty()) {
            return "";
        }
        return String.join("\n\n", entries.stream().map(MemoryEntryEntity::getContent).toList());
    }

    private long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
