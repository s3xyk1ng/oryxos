package com.oryxos.provider;

import com.oryxos.core.model.ProviderDef;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;

/** 按 ProviderDef.type 构建 ChatModel 实例，使用指纹缓存。 fingerprint = apiKey + "|" + baseUrl，配置变化自动重建。 */
public class ProviderChatModelFactory {

    private static final Logger log = LoggerFactory.getLogger(ProviderChatModelFactory.class);

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /** 缓存条目：指纹 + ChatModel 实例。 */
    record CacheEntry(String fingerprint, ChatModel model) {}

    /** 获取或构建 ChatModel。配置未变时复用缓存，变化时重建。 */
    public ChatModel resolve(ProviderDef def) {
        String newFingerprint = def.apiKey() + "|" + def.baseUrl();

        CacheEntry entry =
                cache.compute(
                        def.name(),
                        (key, existing) -> {
                            if (existing != null && existing.fingerprint().equals(newFingerprint)) {
                                return existing;
                            }
                            log.info(
                                    "Building ChatModel for provider '{}' (type={})",
                                    def.name(),
                                    def.type());
                            ChatModel model = buildChatModel(def);
                            return new CacheEntry(newFingerprint, model);
                        });
        return entry.model();
    }

    private ChatModel buildChatModel(ProviderDef def) {
        String baseUrl = def.baseUrl();
        switch (def.type()) {
            case "openai":
                if (baseUrl == null || baseUrl.isEmpty()) {
                    baseUrl = "https://api.openai.com";
                }
                return OpenAiChatModel.builder()
                        .openAiApi(new OpenAiApi(baseUrl, def.apiKey()))
                        .build();
            case "qwen":
            case "openai-compatible":
                if (baseUrl == null || baseUrl.isEmpty()) {
                    throw new IllegalArgumentException(
                            "baseUrl is required for provider type: " + def.type());
                }
                return OpenAiChatModel.builder()
                        .openAiApi(new OpenAiApi(baseUrl, def.apiKey()))
                        .build();
            case "azure-openai":
                // TODO: 需要引入 spring-ai-azure-openai 依赖后实现
                throw new UnsupportedOperationException(
                        "azure-openai provider type is not yet supported. "
                                + "Add spring-ai-azure-openai dependency to implement.");
            default:
                throw new IllegalArgumentException("Unsupported provider type: " + def.type());
        }
    }
}
