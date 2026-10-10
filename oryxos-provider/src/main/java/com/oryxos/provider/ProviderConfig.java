package com.oryxos.provider;

import com.oryxos.core.context.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Provider 配置：根据 application.yaml 中的 Provider 配置创建 ChatModel Bean。
 * 使用显式名称映射，不靠类型扫描。
 */
@Configuration
public class ProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(ProviderConfig.class);

    private final ProviderService providerService;
    private final ConfigLoader configLoader;

    @Value("${oryxos.providers:[]}")
    private String providersRaw;

    // Use configuration properties binding
    private final OryxosProperties oryxosProperties;

    public ProviderConfig(ProviderService providerService, ConfigLoader configLoader,
                          OryxosProperties oryxosProperties) {
        this.providerService = providerService;
        this.configLoader = configLoader;
        this.oryxosProperties = oryxosProperties;
    }

    @PostConstruct
    public void initProviders() {
        List<OryxosProperties.ProviderEntry> providers = oryxosProperties.getProviders();
        if (providers == null || providers.isEmpty()) {
            log.warn("No providers configured. Set oryxos.providers in application.yaml");
            return;
        }

        for (OryxosProperties.ProviderEntry entry : providers) {
            try {
                String apiKey = configLoader.resolve("${" + entry.getApiKeyEnv() + "}");
                ChatModel chatModel = createChatModel(entry, apiKey);
                providerService.registerChatModel(entry.getName(), chatModel);
                log.info("Initialized provider: {} (model: {})", entry.getName(), entry.getModel());
            } catch (Exception e) {
                log.error("Failed to initialize provider {}: {}", entry.getName(), e.getMessage());
            }
        }
    }

    private ChatModel createChatModel(OryxosProperties.ProviderEntry entry, String apiKey) {
        String baseUrl = entry.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.openai.com";
        }

        OpenAiApi openAiApi = new OpenAiApi(baseUrl, apiKey);
        return new OpenAiChatModel(openAiApi,
                OpenAiChatOptions.builder()
                        .model(entry.getModel())
                        .build());
    }

    /**
     * OryxOS 配置属性绑定。
     */
    @Configuration
    @ConfigurationProperties(prefix = "oryxos")
    public static class OryxosProperties {
        private List<ProviderEntry> providers = new ArrayList<>();

        public List<ProviderEntry> getProviders() { return providers; }
        public void setProviders(List<ProviderEntry> providers) { this.providers = providers; }

        public static class ProviderEntry {
            private String name;
            private String model;
            private String apiKeyEnv;
            private String baseUrl;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }

            public String getModel() { return model; }
            public void setModel(String model) { this.model = model; }

            public String getApiKeyEnv() { return apiKeyEnv; }
            public void setApiKeyEnv(String apiKeyEnv) { this.apiKeyEnv = apiKeyEnv; }

            public String getBaseUrl() { return baseUrl; }
            public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        }
    }
}
