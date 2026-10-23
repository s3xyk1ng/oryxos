package com.oryxos.tool.annotation;

import com.oryxos.core.model.OryxTool;
import com.oryxos.core.model.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将 @Tool 注解标注的方法包装为 OryxTool。
 * 通过反射调用原方法，支持返回 String 或 ToolResult。
 */
public class AnnotatedToolAdapter implements OryxTool {

    private static final Logger log = LoggerFactory.getLogger(AnnotatedToolAdapter.class);

    private final String name;
    private final String description;
    private final Object bean;
    private final Method method;

    public AnnotatedToolAdapter(Object bean, Method method, Tool annotation) {
        this.bean = bean;
        this.method = method;
        this.name = annotation.name();
        this.description = annotation.description();
        this.method.setAccessible(true);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of());
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> input) {
        try {
            Object result;
            if (method.getParameterCount() == 0) {
                result = method.invoke(bean);
            } else if (method.getParameterCount() == 1
                    && Map.class.isAssignableFrom(method.getParameterTypes()[0])) {
                result = method.invoke(bean, input);
            } else {
                return ToolResult.fail("Unsupported method signature: " + method.getName(), false);
            }

            if (result instanceof ToolResult tr) {
                return tr;
            } else if (result instanceof String s) {
                return ToolResult.ok(s);
            } else if (result == null) {
                return ToolResult.ok("null");
            } else {
                return ToolResult.ok(result.toString());
            }
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable cause = ite.getCause() != null ? ite.getCause() : ite;
            log.error("@Tool method '{}' invocation failed: {}", name, cause.getMessage());
            return ToolResult.fail("Method invocation failed: " + cause.getMessage(), true);
        } catch (Exception e) {
            log.error("@Tool method '{}' invocation failed: {}", name, e.getMessage());
            return ToolResult.fail("Method invocation failed: " + e.getMessage(), true);
        }
    }
}
