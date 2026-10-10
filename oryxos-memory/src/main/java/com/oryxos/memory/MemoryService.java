package com.oryxos.memory;

import com.oryxos.core.agent.SessionManager;
import com.oryxos.core.model.Message;
import com.oryxos.core.model.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 记忆服务（统一门面）：对 ReAct 循环暴露统一接口。
 * 内部委托 SessionManager（会话记忆）+ LongTermMemoryStore（长期记忆）。
 */
@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final LongTermMemoryStore longTermStore;
    private final SessionManager sessionManager;

    public MemoryService(LongTermMemoryStore longTermStore, SessionManager sessionManager) {
        this.longTermStore = longTermStore;
        this.sessionManager = sessionManager;
    }

    /**
     * 获取指定 Session 的记忆上下文（会话历史 + 长期记忆），用于注入 Prompt。
     */
    public String getContext(Session session) {
        StringBuilder sb = new StringBuilder();

        // Long-term memory
        LongTermMemoryStore.MemoryContent memory = longTermStore.load();
        if (!memory.isEmpty()) {
            sb.append(memory.toPromptString()).append("\n\n");
        }

        // Session conversation history
        List<Message> messages = session.getMessages();
        if (!messages.isEmpty()) {
            sb.append("## Conversation History\n");
            for (Message msg : messages) {
                sb.append("[").append(msg.getRole()).append("] ").append(msg.getContent()).append("\n");
            }
        }

        return sb.toString().trim();
    }

    /**
     * 保存记忆。
     */
    public void saveMemory(String content, LongTermMemoryStore.Scope scope) {
        longTermStore.append(content, scope);
        log.info("Saved {} memory: {}", scope, truncate(content, 60));
    }

    /**
     * 按关键词检索记忆。
     */
    public List<String> recallMemory(String query) {
        return longTermStore.recallByKeyword(query);
    }

    /**
     * 获取长期记忆内容。
     */
    public LongTermMemoryStore.MemoryContent getLongTermMemory() {
        return longTermStore.load();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
