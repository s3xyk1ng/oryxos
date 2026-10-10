package com.oryxos.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provider 服务：维护 provider name → ChatModel 显式映射。
 * 不靠类型扫描 Spring 容器中的 ChatModel Bean。
 */
@Service
public class ProviderService {

    private static final Logger log = LoggerFactory.getLogger(ProviderService.class);
    private final Map<String, ChatModel> chatModels = new ConcurrentHashMap<>();

    /**
     * 注册一个 ChatModel（由 ProviderConfig 在启动时调用）。
     */
    public void registerChatModel(String name, ChatModel chatModel) {
        chatModels.put(name, chatModel);
        log.info("Registered ChatModel for provider: {}", name);
    }

    /**
     * 按名称获取 ChatModel。
     *
     * @throws IllegalArgumentException 如果 provider 不存在
     */
    public ChatModel getChatModel(String name) {
        ChatModel model = chatModels.get(name);
        if (model == null) {
            throw new IllegalArgumentException("Unknown provider: " + name
                    + ". Available: " + chatModels.keySet());
        }
        return model;
    }

    /**
     * 获取所有已注册的 provider 名称。
     */
    public java.util.Set<String> getProviderNames() {
        return chatModels.keySet();
    }

    /**
     * 检查 provider 是否已注册。
     */
    public boolean hasProvider(String name) {
        return chatModels.containsKey(name);
    }
}
