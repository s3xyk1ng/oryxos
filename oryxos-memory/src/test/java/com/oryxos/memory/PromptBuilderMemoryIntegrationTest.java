package com.oryxos.memory;

import com.oryxos.core.context.ContextLoader;
import com.oryxos.core.model.Message;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import com.oryxos.core.react.PromptBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.prompt.Prompt;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PromptBuilder 与 Memory 集成测试。
 */
class PromptBuilderMemoryIntegrationTest {

    @TempDir
    Path tempDir;

    private PromptBuilder promptBuilder;
    private MemoryService memoryService;
    private Profile profile;

    @BeforeEach
    void setUp() {
        LongTermMemoryStore store = new MarkdownMemoryStore(tempDir.resolve("MEMORY.md").toString(), 200);
        memoryService = new MemoryService(store);
        MemoryPortImpl memoryPort = new MemoryPortImpl(memoryService);
        promptBuilder = new PromptBuilder(new ContextLoader(tempDir), memoryPort);
        profile = new Profile();
        profile.setName("default");
    }

    @Test
    @DisplayName("CORE 记忆在 system prompt 中只出现一次")
    void coreMemoryAppearsExactlyOnce() {
        Session session = new Session("s1", "default", "cli", "u1");
        memoryService.saveMemory("project codename ORYXOS", MemoryScope.CORE);

        String systemPrompt = promptBuilder.buildSystemPrompt(profile, session);

        assertThat(systemPrompt).contains("project codename ORYXOS");
        int count = countOccurrences(systemPrompt, "project codename ORYXOS");
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("会话历史在 chat prompt 中不重复")
    void conversationHistoryIsNotDuplicated() {
        Session session = new Session("s1", "default", "cli", "u1");
        session.addMessage(new Message("user", "hello"));
        memoryService.saveMemory("project codename ORYXOS", MemoryScope.CORE);

        String systemPrompt = promptBuilder.buildSystemPrompt(profile, session);
        Prompt prompt = promptBuilder.buildChatPrompt(systemPrompt, session.getMessages(), List.of());

        long userMessageCount = prompt.getInstructions().stream()
                .filter(m -> m instanceof org.springframework.ai.chat.messages.UserMessage)
                .count();
        assertThat(userMessageCount).isEqualTo(1);
    }

    @Test
    @DisplayName("空记忆不破坏 Prompt 结构")
    void emptyMemoryDoesNotBreakPrompt() {
        Session session = new Session("s1", "default", "cli", "u1");
        session.addMessage(new Message("user", "hi"));

        String systemPrompt = promptBuilder.buildSystemPrompt(profile, session);
        Prompt prompt = promptBuilder.buildChatPrompt(systemPrompt, session.getMessages(), List.of());

        assertThat(prompt.getInstructions()).isNotEmpty();
        assertThat(systemPrompt).doesNotContain("## Core Memory");
    }

    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
}
