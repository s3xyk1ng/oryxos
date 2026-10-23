package com.oryxos.tool.annotation;

import com.oryxos.core.model.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AnnotatedToolAdapter 测试（5 用例）。
 */
@DisplayName("AnnotatedToolAdapter 测试")
class AnnotatedToolAdapterTest {

    // --- 测试用 Bean ---
    static class SampleBean {
        @Tool(name = "greet", description = "Say hello")
        public String greet(Map<String, Object> input) {
            return "Hello, " + input.getOrDefault("name", "world");
        }

        @Tool(name = "calc", description = "Add numbers")
        public ToolResult calc(Map<String, Object> input) {
            int a = ((Number) input.get("a")).intValue();
            int b = ((Number) input.get("b")).intValue();
            return ToolResult.ok(String.valueOf(a + b));
        }

        @Tool(name = "fail_tool", description = "Always fails")
        public String failTool(Map<String, Object> input) {
            throw new RuntimeException("intentional error");
        }

        @Tool(name = "no_args", description = "No arguments")
        public String noArgs() {
            return "no-arg-result";
        }

        // This method should NOT be scanned (no @Tool annotation)
        public String notATool() {
            return "should not be registered";
        }
    }

    private AnnotatedToolAdapter adapterFor(String methodName) throws Exception {
        for (Method m : SampleBean.class.getDeclaredMethods()) {
            if (m.getName().equals(methodName) && m.isAnnotationPresent(Tool.class)) {
                return new AnnotatedToolAdapter(new SampleBean(), m, m.getAnnotation(Tool.class));
            }
        }
        throw new IllegalArgumentException("No @Tool method found: " + methodName);
    }

    @Test
    @DisplayName("名称：返回 @Tool.name()")
    void nameFromAnnotation() throws Exception {
        var adapter = adapterFor("greet");
        assertEquals("greet", adapter.getName());
    }

    @Test
    @DisplayName("描述：返回 @Tool.description()")
    void descriptionFromAnnotation() throws Exception {
        var adapter = adapterFor("greet");
        assertEquals("Say hello", adapter.getDescription());
    }

    @Test
    @DisplayName("Schema：返回合法 JSON Schema 结构")
    void schemaIsValid() throws Exception {
        var adapter = adapterFor("greet");
        Map<String, Object> schema = adapter.getInputSchema();
        assertNotNull(schema);
        assertEquals("object", schema.get("type"));
        assertNotNull(schema.get("properties"));
    }

    @Test
    @DisplayName("执行：反射调用方法并返回结果")
    void executeReturnsResult() throws Exception {
        // String return type
        var greetAdapter = adapterFor("greet");
        ToolResult result = greetAdapter.execute(Map.of("name", "OryxOS"));
        assertTrue(result.isSuccess());
        assertEquals("Hello, OryxOS", result.getContent());

        // ToolResult return type
        var calcAdapter = adapterFor("calc");
        ToolResult calcResult = calcAdapter.execute(Map.of("a", 3, "b", 4));
        assertTrue(calcResult.isSuccess());
        assertEquals("7", calcResult.getContent());

        // No-arg method
        var noArgsAdapter = adapterFor("noArgs");
        ToolResult noArgsResult = noArgsAdapter.execute(Map.of());
        assertTrue(noArgsResult.isSuccess());
        assertEquals("no-arg-result", noArgsResult.getContent());
    }

    @Test
    @DisplayName("异常：方法抛异常时返回 ToolResult.fail(retryable=true)")
    void executeExceptionReturnsFail() throws Exception {
        var adapter = adapterFor("failTool");
        ToolResult result = adapter.execute(Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.isRetryable());
        assertTrue(result.getError().contains("intentional error"));
    }
}
