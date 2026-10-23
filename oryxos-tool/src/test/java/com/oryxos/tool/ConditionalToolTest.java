package com.oryxos.tool;

import com.oryxos.core.agent.ToolRegistry;
import com.oryxos.core.model.ConditionalTool;
import com.oryxos.core.model.OryxTool;
import com.oryxos.core.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ConditionalTool 条件注册测试（3 用例）。
 */
@DisplayName("ConditionalTool 条件注册测试")
class ConditionalToolTest {

    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
    }

    @Test
    @DisplayName("available 的工具被注册")
    void availableToolIsRegistered() {
        OryxTool availableTool = new TestConditionalTool("cond_available", true);

        ToolAutoRegistrar registrar = new ToolAutoRegistrar(
                toolRegistry, List.of(availableTool));
        registrar.run(mock(org.springframework.boot.ApplicationArguments.class));

        assertNotNull(toolRegistry.getTool("cond_available"));
    }

    @Test
    @DisplayName("unavailable 的工具不注册")
    void unavailableToolIsNotRegistered() {
        OryxTool unavailableTool = new TestConditionalTool("cond_unavailable", false);

        ToolAutoRegistrar registrar = new ToolAutoRegistrar(
                toolRegistry, List.of(unavailableTool));
        registrar.run(mock(org.springframework.boot.ApplicationArguments.class));

        assertNull(toolRegistry.getTool("cond_unavailable"));
    }

    @Test
    @DisplayName("非 ConditionalTool 的普通工具正常注册")
    void regularToolAlwaysRegistered() {
        OryxTool regularTool = mock(OryxTool.class);
        when(regularTool.getName()).thenReturn("regular_tool");

        ToolAutoRegistrar registrar = new ToolAutoRegistrar(
                toolRegistry, List.of(regularTool));
        registrar.run(mock(org.springframework.boot.ApplicationArguments.class));

        assertNotNull(toolRegistry.getTool("regular_tool"));
    }

    /**
     * 测试用 ConditionalTool 实现。
     */
    private static class TestConditionalTool implements OryxTool, ConditionalTool {
        private final String name;
        private final boolean available;

        TestConditionalTool(String name, boolean available) {
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
