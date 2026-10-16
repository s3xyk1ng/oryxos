package com.oryxos.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.oryxos.core.agent.ProviderRegistry;
import com.oryxos.core.model.ProviderDef;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProviderBootstrapTest {

    @Test
    @DisplayName("load_from_yaml：从 ProvidersProperties 加载到 Registry")
    void loadFromYaml() {
        // Arrange
        ProvidersProperties props = new ProvidersProperties();
        ProvidersProperties.ProviderConfig config1 = new ProvidersProperties.ProviderConfig();
        config1.setName("qwen-plus");
        config1.setType("qwen");
        config1.setApiKey("key1");
        config1.setBaseUrl("https://dashscope.example.com/v1");

        ProvidersProperties.ProviderConfig config2 = new ProvidersProperties.ProviderConfig();
        config2.setName("gpt-4o");
        config2.setType("openai");
        config2.setApiKey("key2");

        props.setProviders(List.of(config1, config2));

        InMemoryProviderRegistry registry = new InMemoryProviderRegistry();
        ProviderBootstrap bootstrap = new ProviderBootstrap(registry, props);

        // Act
        bootstrap.run(null);

        // Assert
        ProviderRegistry reg = registry;
        ProviderDef found1 = reg.find("qwen-plus");
        ProviderDef found2 = reg.find("gpt-4o");
        assertNotNull(found1);
        assertNotNull(found2);
        assertEquals("qwen", found1.type());
        assertEquals("openai", found2.type());
        assertEquals(2, registry.size());
    }
}
