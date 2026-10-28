package com.oryxos.memory;

import com.oryxos.core.model.Message;
import com.oryxos.core.model.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MemoryService 上层门面测试。
 */
class MemoryServiceTest {

    @TempDir
    Path tempDir;

    private MemoryService memoryService;
    private LongTermMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new MarkdownMemoryStore(tempDir.resolve("MEMORY.md").toString(), 200);
        memoryService = new MemoryService(store);
    }

    @Test
    @DisplayName("Prompt 上下文包含完整 CORE 和当前会话历史")
    void buildPromptContext_includesCoreAndSessionHistory() {
        Session session = new Session("test-session", "default", "cli", "user-1");
        session.addMessage(new Message("user", "hello"));
        store.append("project codename ORYXOS", MemoryScope.CORE);

        String context = memoryService.buildPromptContext(session);

        assertThat(context).contains("project codename ORYXOS");
        assertThat(context).contains("[user] hello");
    }

    @Test
    @DisplayName("Prompt 上下文不自动注入 ARCHIVAL")
    void buildPromptContext_excludesArchivalMemory() {
        Session session = new Session("test-session", "default", "cli", "user-1");
        store.append("archival note", MemoryScope.ARCHIVAL);

        String context = memoryService.buildPromptContext(session);

        assertThat(context).doesNotContain("archival note");
    }

    @Test
    @DisplayName("每次构建 Prompt 上下文都重新加载")
    void buildPromptContext_reloadsMemoryEveryTime() {
        Session session = new Session("test-session", "default", "cli", "user-1");
        String first = memoryService.buildPromptContext(session);
        assertThat(first).doesNotContain("new core fact");

        store.append("new core fact", MemoryScope.CORE);
        String second = memoryService.buildPromptContext(session);
        assertThat(second).contains("new core fact");
    }

    @Test
    @DisplayName("saveMemory 原样传递 scope 给后端")
    void saveMemory_delegatesScopeUnchanged() {
        memoryService.saveMemory("core fact", MemoryScope.CORE);
        memoryService.saveMemory("archival fact", MemoryScope.ARCHIVAL);

        MemoryContent loaded = store.load();
        assertThat(loaded.coreSection()).contains("core fact");
        assertThat(loaded.archivalSection()).contains("archival fact");
    }

    @Test
    @DisplayName("recallMemory 原样传递 keyword 给后端")
    void recallMemory_delegatesKeyword() {
        memoryService.saveMemory("unique archival content", MemoryScope.ARCHIVAL);

        assertThat(memoryService.recallMemory("unique archival"))
                .hasSize(1)
                .contains("unique archival content");
    }

    @Test
    @DisplayName("空白内容保存被拒绝")
    void saveMemory_rejectsBlankContent() {
        assertThatThrownBy(() -> memoryService.saveMemory("  ", MemoryScope.CORE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("缺失 scope 保存被拒绝")
    void saveMemory_rejectsNullScope() {
        assertThatThrownBy(() -> memoryService.saveMemory("content", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
