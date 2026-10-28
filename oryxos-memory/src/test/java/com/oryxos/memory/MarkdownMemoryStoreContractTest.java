package com.oryxos.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Markdown 后端契约测试。
 */
class MarkdownMemoryStoreContractTest extends LongTermMemoryStoreContractTest {

    @TempDir
    Path tempDir;

    private Path memoryFile;

    @BeforeEach
    void setUp() {
        memoryFile = tempDir.resolve("MEMORY.md");
    }

    @Override
    protected LongTermMemoryStore createStore() {
        return new MarkdownMemoryStore(memoryFile.toString(), 200);
    }

    @Override
    protected LongTermMemoryStore createAnotherStore() {
        return new MarkdownMemoryStore(memoryFile.toString(), 200);
    }

    @Test
    void append_createsMemoryFileAndParentDirectories() {
        Path nested = tempDir.resolve("nested").resolve("MEMORY.md");
        MarkdownMemoryStore store = new MarkdownMemoryStore(nested.toString(), 200);
        store.append("first entry", MemoryScope.CORE);
        assertThat(nested).exists();
    }

    @Test
    void load_preservesMultilineEntryBoundaries() {
        LongTermMemoryStore store = createStore();
        String entry = "Line one\nLine two\nLine three";
        store.append(entry, MemoryScope.ARCHIVAL);

        MemoryContent loaded = store.load();
        assertThat(loaded.archivalSection()).contains("Line one");
        assertThat(loaded.archivalSection()).contains("Line two");
        assertThat(loaded.archivalSection()).contains("Line three");
    }

    @Test
    void recall_doesNotReadCoreSection() {
        LongTermMemoryStore store = createStore();
        String keyword = "SECRET-" + System.currentTimeMillis();
        store.append(keyword, MemoryScope.CORE);
        store.append("archival " + keyword, MemoryScope.ARCHIVAL);

        assertThat(store.recallByKeyword(keyword))
                .hasSize(1)
                .allMatch(s -> s.startsWith("archival"));
    }
}
