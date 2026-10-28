package com.oryxos.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 三档后端共同契约测试。
 * 所有具体后端实现必须继承本类并提供一个连接到真实后端的 Store 实例。
 */
public abstract class LongTermMemoryStoreContractTest {

    /**
     * 子类提供待测 Store。
     */
    protected abstract LongTermMemoryStore createStore();

    /**
     * 子类提供另一个连接到同一后端的 Store 实例，用于验证无缓存。
     */
    protected abstract LongTermMemoryStore createAnotherStore();

    @Test
    @DisplayName("写入后通过另一个 Store 实例立即可见")
    void load_afterExternalAppendReturnsLatestState() {
        LongTermMemoryStore storeA = createStore();
        LongTermMemoryStore storeB = createAnotherStore();

        String keyword = "EXTERNAL-" + UUID.randomUUID();
        storeA.append(keyword, MemoryScope.CORE);

        MemoryContent loaded = storeB.load();
        assertThat(loaded.coreSection()).contains(keyword);
    }

    @Test
    @DisplayName("CORE 区永不截断")
    void load_coreIsNeverTruncated() {
        LongTermMemoryStore store = createStore();

        for (int i = 0; i < 10; i++) {
            store.append("CORE-LINE-" + i + "-" + UUID.randomUUID(), MemoryScope.CORE);
        }
        store.append("CORE-MULTI\nLINE\nCONTENT-" + UUID.randomUUID(), MemoryScope.CORE);

        MemoryContent loaded = store.load();
        for (int i = 0; i < 10; i++) {
            assertThat(loaded.coreSection()).contains("CORE-LINE-" + i);
        }
        assertThat(loaded.coreSection()).contains("CORE-MULTI");
        assertThat(loaded.coreSection()).contains("CONTENT");
    }

    @Test
    @DisplayName("ARCHIVAL 只返回最新 N 条")
    void load_archivalReturnsLatestNOnly() {
        LongTermMemoryStore store = createStore();

        for (int i = 0; i < 5; i++) {
            store.append("ARCHIVAL-" + i + "-" + UUID.randomUUID(), MemoryScope.ARCHIVAL);
        }

        MemoryContent loaded = store.load();
        // 默认窗口 200，5 条应全部返回
        assertThat(loaded.archivalSection()).contains("ARCHIVAL-4");
        assertThat(loaded.archivalSection()).contains("ARCHIVAL-0");
    }

    @Test
    @DisplayName("ARCHIVAL 截断不物理删除旧条目")
    void load_truncationDoesNotDeleteArchivalEntries() {
        LongTermMemoryStore store = createStore();

        String oldKeyword = "OLD-ARCHIVAL-" + UUID.randomUUID();
        store.append(oldKeyword, MemoryScope.ARCHIVAL);
        for (int i = 0; i < 250; i++) {
            store.append("FILLER-" + i + "-" + UUID.randomUUID(), MemoryScope.ARCHIVAL);
        }

        MemoryContent loaded = store.load();
        assertThat(loaded.archivalSection()).doesNotContain(oldKeyword);

        List<String> recalled = store.recallByKeyword(oldKeyword);
        assertThat(recalled).contains(oldKeyword);
    }

    @Test
    @DisplayName("append 保留 Agent 显式选择的 scope")
    void append_preservesAgentSelectedScope() {
        LongTermMemoryStore store = createStore();

        String coreKeyword = "AGENT-CORE-" + UUID.randomUUID();
        String archivalKeyword = "AGENT-ARCHIVAL-" + UUID.randomUUID();
        store.append(coreKeyword, MemoryScope.CORE);
        store.append(archivalKeyword, MemoryScope.ARCHIVAL);

        MemoryContent loaded = store.load();
        assertThat(loaded.coreSection()).contains(coreKeyword);
        assertThat(loaded.coreSection()).doesNotContain(archivalKeyword);
        assertThat(loaded.archivalSection()).contains(archivalKeyword);
    }

    @Test
    @DisplayName("recall 只检索 ARCHIVAL，不返回 CORE")
    void recall_searchesArchivalOnly() {
        LongTermMemoryStore store = createStore();

        String keyword = "SHARED-" + UUID.randomUUID();
        store.append("CORE-" + keyword, MemoryScope.CORE);
        store.append("ARCHIVAL-" + keyword, MemoryScope.ARCHIVAL);

        List<String> recalled = store.recallByKeyword(keyword);
        assertThat(recalled).anyMatch(s -> s.contains("ARCHIVAL-" + keyword));
        assertThat(recalled).noneMatch(s -> s.contains("CORE-" + keyword));
    }

    @Test
    @DisplayName("多行记忆命中一行时返回完整条目")
    void recall_returnsCompleteMultilineEntry() {
        LongTermMemoryStore store = createStore();

        String keyword = "HIT-ON-SECOND-" + UUID.randomUUID();
        String entry = "First line\n" + keyword + "\nThird line";
        store.append(entry, MemoryScope.ARCHIVAL);

        List<String> recalled = store.recallByKeyword(keyword);
        assertThat(recalled).hasSize(1);
        assertThat(recalled.get(0)).isEqualTo(entry);
    }

    @Test
    @DisplayName("关键词按普通文本处理，无 LIKE/正则语义")
    void recall_treatsKeywordAsLiteralText() {
        LongTermMemoryStore store = createStore();

        store.append("Contains 50% of effort", MemoryScope.ARCHIVAL);
        store.append("Contains _underscore_", MemoryScope.ARCHIVAL);
        store.append("Contains .* regex", MemoryScope.ARCHIVAL);
        store.append("Contains [a-z] range", MemoryScope.ARCHIVAL);

        assertThat(store.recallByKeyword("50%")).hasSize(1);
        assertThat(store.recallByKeyword("_underscore_")).hasSize(1);
        assertThat(store.recallByKeyword(".*")).hasSize(1);
        assertThat(store.recallByKeyword("[a-z]")).hasSize(1);
    }

    @Test
    @DisplayName("无匹配返回空列表")
    void recall_withoutMatchReturnsEmpty() {
        LongTermMemoryStore store = createStore();

        store.append("Some archival content", MemoryScope.ARCHIVAL);

        List<String> recalled = store.recallByKeyword("NOT-EXISTING-" + UUID.randomUUID());
        assertThat(recalled).isEmpty();
    }
}
