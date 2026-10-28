package com.oryxos.memory;

import com.oryxos.core.model.OryxTool;
import com.oryxos.core.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MemoryTools 工具行为测试。
 */
class MemoryToolsTest {

    @TempDir
    Path tempDir;

    private MemoryTools memoryTools;
    private OryxTool saveTool;
    private OryxTool recallTool;
    private LongTermMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new MarkdownMemoryStore(tempDir.resolve("MEMORY.md").toString(), 200);
        MemoryService memoryService = new MemoryService(store);
        memoryTools = new MemoryTools(memoryService);
        saveTool = memoryTools.new SaveMemoryTool();
        recallTool = memoryTools.new RecallMemoryTool();
    }

    @Test
    @DisplayName("save_memory 拒绝空白 content")
    void saveMemory_rejectsBlankContent() {
        ToolResult result = saveTool.execute(Map.of("content", "   ", "scope", "CORE"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("content");
    }

    @Test
    @DisplayName("save_memory 拒绝缺失 scope")
    void saveMemory_rejectsMissingScope() {
        ToolResult result = saveTool.execute(Map.of("content", "important fact"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("scope");
    }

    @Test
    @DisplayName("save_memory 接受 CORE 并持久化")
    void saveMemory_acceptsCore() {
        ToolResult result = saveTool.execute(Map.of("content", "project codename ORYXOS", "scope", "CORE"));
        assertThat(result.isSuccess()).isTrue();
        assertThat(store.load().coreSection()).contains("project codename ORYXOS");
    }

    @Test
    @DisplayName("save_memory 接受 ARCHIVAL 并持久化")
    void saveMemory_acceptsArchival() {
        ToolResult result = saveTool.execute(Map.of("content", "release checklist", "scope", "ARCHIVAL"));
        assertThat(result.isSuccess()).isTrue();
        assertThat(store.load().archivalSection()).contains("release checklist");
    }

    @Test
    @DisplayName("recall_memory 命中 ARCHIVAL 条目")
    void recallMemory_returnsMatchingEntries() {
        saveTool.execute(Map.of("content", "unique archival content", "scope", "ARCHIVAL"));

        ToolResult result = recallTool.execute(Map.of("query", "unique archival"));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContent()).contains("unique archival content");
    }

    @Test
    @DisplayName("recall_memory 无匹配返回空")
    void recallMemory_returnsEmptyWhenNoMatch() {
        saveTool.execute(Map.of("content", "some note", "scope", "ARCHIVAL"));

        ToolResult result = recallTool.execute(Map.of("query", "missing"));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContent()).contains("No memories found");
    }
}
