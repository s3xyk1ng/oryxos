package com.oryxos.memory;

import com.oryxos.storage.repository.MemoryEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 长期记忆后端条件装配。
 * 通过 {@code oryxos.memory.backend} 选择 Markdown / SQLite / Mem0 三档后端，
 * 任意时刻只有一个 {@link LongTermMemoryStore} Bean 生效。
 */
@Configuration
@EnableConfigurationProperties(MemoryProperties.class)
public class MemoryStoreConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MemoryStoreConfiguration.class);

    @Bean
    @ConditionalOnProperty(prefix = "oryxos.memory", name = "backend", havingValue = "markdown", matchIfMissing = true)
    public LongTermMemoryStore markdownMemoryStore(MemoryProperties properties) {
        log.info("Memory backend selected: markdown");
        return new MarkdownMemoryStore(properties.getMarkdown().getPath(), properties.getArchivalLimit());
    }

    @Bean
    @ConditionalOnProperty(prefix = "oryxos.memory", name = "backend", havingValue = "sqlite")
    public LongTermMemoryStore sqliteMemoryStore(MemoryEntryRepository repository, MemoryProperties properties) {
        log.info("Memory backend selected: sqlite");
        return new SqliteMemoryStore(repository, properties.getArchivalLimit());
    }

    @Bean
    @ConditionalOnProperty(prefix = "oryxos.memory", name = "backend", havingValue = "mem0")
    public LongTermMemoryStore mem0MemoryStore(MemoryProperties properties) {
        log.info("Memory backend selected: mem0");
        MemoryProperties.Mem0 mem0 = properties.getMem0();
        return new Mem0MemoryStore(mem0.getBaseUrl(), mem0.getApiKey(), properties.getArchivalLimit());
    }
}
