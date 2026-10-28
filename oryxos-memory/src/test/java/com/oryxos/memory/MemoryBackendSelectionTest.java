package com.oryxos.memory;

import com.oryxos.storage.repository.MemoryEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 后端条件装配测试：任意时刻只有一个 LongTermMemoryStore Bean 生效。
 */
class MemoryBackendSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MemoryStoreConfiguration.class)
            .withBean(MemoryEntryRepository.class, () -> org.mockito.Mockito.mock(MemoryEntryRepository.class));

    @Test
    void defaultBackendIsMarkdown() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LongTermMemoryStore.class);
            assertThat(context.getBean(LongTermMemoryStore.class)).isInstanceOf(MarkdownMemoryStore.class);
        });
    }

    @Test
    void sqliteBackendCanBeSelected() {
        contextRunner
                .withPropertyValues("oryxos.memory.backend=sqlite")
                .run(context -> {
                    assertThat(context).hasSingleBean(LongTermMemoryStore.class);
                    assertThat(context.getBean(LongTermMemoryStore.class)).isInstanceOf(SqliteMemoryStore.class);
                });
    }

    @Test
    void mem0BackendCanBeSelected() {
        contextRunner
                .withPropertyValues(
                        "oryxos.memory.backend=mem0",
                        "oryxos.memory.mem0.base-url=http://localhost:8000",
                        "oryxos.memory.mem0.api-key=${MEM0_API_KEY}")
                .run(context -> {
                    assertThat(context).hasSingleBean(LongTermMemoryStore.class);
                    assertThat(context.getBean(LongTermMemoryStore.class)).isInstanceOf(Mem0MemoryStore.class);
                });
    }

    @Test
    void onlyOneBackendBeanExistsAtAnyTime() {
        contextRunner
                .withPropertyValues("oryxos.memory.backend=sqlite")
                .run(context -> {
                    assertThat(context.getBeansOfType(LongTermMemoryStore.class)).hasSize(1);
                    assertThat(context).doesNotHaveBean(MarkdownMemoryStore.class);
                    assertThat(context).doesNotHaveBean(Mem0MemoryStore.class);
                });
    }
}
