package com.oryxos.tool.annotation;

import com.oryxos.core.agent.ToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * ToolMethodScanner 测试（3 用例）。
 */
@DisplayName("ToolMethodScanner 测试")
class ToolMethodScannerTest {

    // --- 测试用配置 ---
    static class MyTools {
        @Tool(name = "scan_test_a", description = "Test tool A")
        public String toolA() {
            return "A";
        }

        @Tool(name = "scan_test_b", description = "Test tool B")
        public String toolB() {
            return "B";
        }

        // Not annotated — should NOT be registered
        public String notATool() {
            return "nope";
        }
    }

    @Configuration
    static class TestConfig {
        @Bean
        public MyTools myTools() {
            return new MyTools();
        }

        @Bean
        public ToolRegistry toolRegistry() {
            return new ToolRegistry();
        }
    }

    @Test
    @DisplayName("扫描：发现所有 @Tool 方法")
    void scanFindsAnnotatedMethods() {
        try (var ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            ToolRegistry registry = ctx.getBean(ToolRegistry.class);
            ToolMethodScanner scanner = new ToolMethodScanner(ctx, registry);

            scanner.run(mock(ApplicationArguments.class));

            assertNotNull(registry.getTool("scan_test_a"));
            assertNotNull(registry.getTool("scan_test_b"));
        }
    }

    @Test
    @DisplayName("注册：扫描到的工具被注册到 ToolRegistry")
    void registersToToolRegistry() {
        try (var ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            ToolRegistry registry = ctx.getBean(ToolRegistry.class);
            ToolMethodScanner scanner = new ToolMethodScanner(ctx, registry);

            scanner.run(mock(ApplicationArguments.class));

            assertEquals(2, registry.getAllTools().size());
        }
    }

    @Test
    @DisplayName("不误注册：没有 @Tool 注解的方法不会被注册")
    void doesNotRegisterNonAnnotatedMethods() {
        try (var ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            ToolRegistry registry = ctx.getBean(ToolRegistry.class);
            ToolMethodScanner scanner = new ToolMethodScanner(ctx, registry);

            scanner.run(mock(ApplicationArguments.class));

            // "notATool" should NOT be registered
            assertNull(registry.getTool("notATool"));
            // Also verify by name patterns
            assertNull(registry.getTool("notATool"));
            assertEquals(2, registry.getAllTools().size());
        }
    }
}
