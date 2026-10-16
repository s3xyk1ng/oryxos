package com.oryxos.provider;

import com.oryxos.core.agent.LlmPortFactory;
import com.oryxos.core.agent.ProviderRegistry;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.ProviderDef;
import com.oryxos.core.react.LlmPort;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/** LlmPortFactory 实现：桥接 ReAct 循环的 LlmPort 和 Provider 模块的 ChatModel 缓存。 */
public class ProviderLlmPortFactory implements LlmPortFactory {

    private final ProviderRegistry registry;
    private final ProviderChatModelFactory chatModelFactory;

    public ProviderLlmPortFactory(
            ProviderRegistry registry, ProviderChatModelFactory chatModelFactory) {
        this.registry = registry;
        this.chatModelFactory = chatModelFactory;
    }

    @Override
    public LlmPort createForProfile(Profile profile) {
        ProviderDef def = registry.find(profile.getProvider());
        ChatModel chatModel = chatModelFactory.resolve(def);
        String modelName = profile.getModel() != null ? profile.getModel() : "default";
        return new ChatModelLlmPort(chatModel, def.name(), modelName);
    }

    /** LlmPort 适配 ChatModel。 */
    private static class ChatModelLlmPort implements LlmPort {

        private final ChatModel chatModel;
        private final String providerName;
        private final String modelName;

        ChatModelLlmPort(ChatModel chatModel, String providerName, String modelName) {
            this.chatModel = chatModel;
            this.providerName = providerName;
            this.modelName = modelName;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return chatModel.call(prompt);
        }

        @Override
        public String getProviderName() {
            return providerName;
        }

        @Override
        public String getModelName() {
            return modelName;
        }
    }
}
