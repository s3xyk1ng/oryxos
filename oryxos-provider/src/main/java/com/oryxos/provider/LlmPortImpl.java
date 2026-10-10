package com.oryxos.provider;

import com.oryxos.core.model.Profile;
import com.oryxos.core.react.LlmPort;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

/**
 * LlmPort 实现：委托给 ProviderService 获取 ChatModel。
 */
public class LlmPortImpl implements LlmPort {

    private final ChatModel chatModel;
    private final String providerName;
    private final String modelName;

    public LlmPortImpl(ChatModel chatModel, String providerName, String modelName) {
        this.chatModel = chatModel;
        this.providerName = providerName;
        this.modelName = modelName;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return chatModel.call(prompt);
    }

    @Override
    public String getProviderName() { return providerName; }

    @Override
    public String getModelName() { return modelName; }
}
