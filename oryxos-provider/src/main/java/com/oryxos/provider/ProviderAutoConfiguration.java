package com.oryxos.provider;

import com.oryxos.storage.repository.LlmCallRepository;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring 自动配置，组装 Provider 模块的 Bean。 */
@Configuration
public class ProviderAutoConfiguration {

    @Bean
    public InMemoryProviderRegistry providerRegistry() {
        return new InMemoryProviderRegistry();
    }

    @Bean
    @ConfigurationProperties("oryxos")
    public ProvidersProperties providersProperties() {
        return new ProvidersProperties();
    }

    @Bean
    public ProviderBootstrap providerBootstrap(
            InMemoryProviderRegistry registry, ProvidersProperties properties) {
        return new ProviderBootstrap(registry, properties);
    }

    @Bean
    public ProviderChatModelFactory providerChatModelFactory() {
        return new ProviderChatModelFactory();
    }

    @Bean
    public ToolSchemaAdapter toolSchemaAdapter() {
        return new ToolSchemaAdapter();
    }

    @Bean
    public AuditLogger auditLogger(LlmCallRepository repository) {
        return new AuditLogger(repository);
    }

    @Bean
    public com.oryxos.core.agent.ProviderService providerService(
            InMemoryProviderRegistry registry,
            ProviderChatModelFactory factory,
            ToolSchemaAdapter toolSchemaAdapter,
            AuditLogger auditLogger) {
        return new SpringAiProviderServiceImpl(registry, factory, toolSchemaAdapter, auditLogger);
    }
}
