package com.oryxos.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.oryxos.core.model.ProviderDef;
import com.oryxos.core.model.ProviderNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryProviderRegistryTest {

    private InMemoryProviderRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryProviderRegistry();
    }

    @Test
    @DisplayName("save + find：注册后按 name 查找返回相同 def")
    void saveAndFind() {
        ProviderDef def =
                new ProviderDef("qwen-plus", "qwen", "test-key", "https://example.com/v1", null);
        registry.save(def);

        ProviderDef found = registry.find("qwen-plus");
        assertSame(def, found);
        assertEquals("qwen-plus", found.name());
        assertEquals("qwen", found.type());
    }

    @Test
    @DisplayName("find 不存在：抛出 ProviderNotFoundException")
    void findNotFound() {
        assertThrows(ProviderNotFoundException.class, () -> registry.find("nonexistent"));
    }
}
