package com.oryxos.provider;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.oryxos.core.model.ProviderDef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

class ProviderChatModelFactoryTest {

    @Test
    @DisplayName("resolve_first_time：首次构建 ChatModel 并缓存")
    void resolveFirstTime() {
        ProviderChatModelFactory factory = new ProviderChatModelFactory();
        ProviderDef def = new ProviderDef("test", "openai", "sk-test-key", null, null);

        ChatModel model = factory.resolve(def);
        assertNotNull(model);
    }

    @Test
    @DisplayName("resolve_cached：相同配置返回缓存实例")
    void resolveCached() {
        ProviderChatModelFactory factory = new ProviderChatModelFactory();
        ProviderDef def = new ProviderDef("test", "openai", "sk-test-key", null, null);

        ChatModel first = factory.resolve(def);
        ChatModel second = factory.resolve(def);
        assertSame(first, second);
    }

    @Test
    @DisplayName("resolve_config_changed：apiKey 变化后重建 ChatModel")
    void resolveConfigChanged() {
        ProviderChatModelFactory factory = new ProviderChatModelFactory();
        ProviderDef def1 = new ProviderDef("test", "openai", "sk-key-1", null, null);
        ProviderDef def2 = new ProviderDef("test", "openai", "sk-key-2", null, null);

        ChatModel first = factory.resolve(def1);
        ChatModel second = factory.resolve(def2);
        assertNotSame(first, second);
    }
}
