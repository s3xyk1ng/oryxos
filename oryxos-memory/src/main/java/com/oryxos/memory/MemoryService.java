package com.oryxos.memory;

import com.oryxos.core.model.Message;
import com.oryxos.core.model.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 记忆服务统一门面。
 * 对上层 ReActLoop / PromptBuilder 暴露三类能力：
 * ① 组装 Prompt 上下文（完整 CORE + 当前会话历史，不含 ARCHIVAL）
 * ② 保存记忆（Agent 显式指定 CORE / ARCHIVAL）
 * ③ 按关键词召回归档记忆
 */
@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final LongTermMemoryStore longTermStore;

    public MemoryService(LongTermMemoryStore longTermStore) {
        this.longTermStore = longTermStore;
    }

    /**
     * 组装 Prompt 上下文：只自动包含完整 CORE 和当前会话历史。
     * ARCHIVAL 不自动注入，必须由 Agent 通过 recall_memory 按需召回。
     */
    public String buildPromptContext(Session session) {
        StringBuilder sb = new StringBuilder();

        MemoryContent memory = longTermStore.load();
        if (!memory.coreSection().isBlank()) {
            sb.append("## Core Memory\n").append(memory.coreSection()).append("\n\n");
        }

        List<Message> messages = session.getMessages();
        if (!messages.isEmpty()) {
            sb.append("## Conversation History\n");
            for (Message msg : messages) {
                sb.append("[").append(msg.getRole()).append("] ").append(msg.getContent()).append("\n");
            }
        }

        String result = sb.toString().trim();
        log.debug("Built prompt context, core length={}, history messages={}",
                memory.coreSection().length(), messages.size());
        return result;
    }

    /**
     * 保存记忆。scope 必须由 Agent 显式指定，禁止默认或推断。
     */
    public void saveMemory(String content, MemoryScope scope) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Memory content must not be blank");
        }
        if (scope == null) {
            throw new IllegalArgumentException("Memory scope must be explicitly CORE or ARCHIVAL");
        }
        longTermStore.append(content, scope);
        log.info("Memory save delegated: scope={}", scope);
    }

    /**
     * 按关键词召回归档记忆。
     */
    public List<String> recallMemory(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("Keyword must not be blank");
        }
        return longTermStore.recallByKeyword(keyword);
    }

}
