package com.oryxos.provider;

import com.oryxos.core.agent.LlmPortFactory;
import com.oryxos.core.model.Profile;
import com.oryxos.core.react.LlmPort;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * LlmPortFactory 实现：根据 Profile 的 provider 名称创建 LlmPort。
 */
@Component
public class LlmPortFactoryImpl implements LlmPortFactory {

    private final ProviderService providerService;

    public LlmPortFactoryImpl(ProviderService providerService) {
        this.providerService = providerService;
    }

    @Override
    public LlmPort createForProfile(Profile profile) {
        ChatModel chatModel = providerService.getChatModel(profile.getProvider());
        // Extract model name from ChatModel's default options if available
        String modelName = profile.getProvider();
        return new LlmPortImpl(chatModel, profile.getProvider(), modelName);
    }
}
