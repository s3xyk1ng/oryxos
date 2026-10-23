package com.oryxos.tool;

import com.oryxos.core.agent.ToolRegistry;
import com.oryxos.core.model.ConditionalTool;
import com.oryxos.core.model.OryxTool;
import com.oryxos.core.model.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ToolAutoRegistrar 测试（2 用例）。
 */
@DisplayName("ToolAutoRegistrar 测试")
class ToolAutoRegistrarTest {

    @Test
    @DisplayName("条件注册：只注册 isAvailable()=true 的 ConditionalTool")
    void conditionalRegistration() {
        ToolRegistry registry = new ToolRegistry();

        OryxTool available = new CondTool("avail", true);
        OryxTool unavailable = new CondTool("unavail", false);
        OryxTool regular = mock(OryxTool.class);
        when(regular.getName()).thenReturn("regular");

        ToolAutoRegistrar registrar = new ToolAutoRegistrar(
                registry, List.of(available, unavailable, regular));
        registrar.run(mock(ApplicationArguments.class));

        assertNotNull(registry.getTool("avail"));
        assertNull(registry.getTool("unavail"));
        assertNotNull(registry.getTool("regular"));
        assertEquals(2, registry.getAllTools().size());
    }

    @Test
    @DisplayName("日志可观测：跳过的工具有日志输出（不抛异常）")
    void loggingObservable() {
        ToolRegistry registry = new ToolRegistry();
        OryxTool unavailable = new CondTool("skip_me", false);

        ToolAutoRegistrar registrar = new ToolAutoRegistrar(
                registry, List.of(unavailable));

        // Should not throw, just log
        assertDoesNotThrow(() -> registrar.run(mock(ApplicationArguments.class)));
        assertEquals(0, registry.getAllTools().size());
    }

    private static class CondTool implements OryxTool, ConditionalTool {
        private final String name;
        private final boolean available;

        CondTool(String name, boolean available) {
            this.name = name;
            this.available = available;
        }

        @Override
        public boolean isAvailable() { return available; }

        @Override
        public String getName() { return name; }

        @Override
        public String getDescription() { return "test"; }

        @Override
        public Map<String, Object> getInputSchema() { return Map.of(); }

        @Override
        public ToolResult execute(Map<String, Object> input) { return ToolResult.ok("ok"); }
    }
}
