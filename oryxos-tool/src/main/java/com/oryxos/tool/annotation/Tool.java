package com.oryxos.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Spring Bean 方法上，框架启动时自动扫描并注册为 OryxTool。
 * 这是 Plugin Tool 重代码路径：Java 开发者用此注解快速扩展工具。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {

    /** 工具名称（如 my_tool），注册到 ToolRegistry 的 key */
    String name();

    /** 工具描述（给 LLM 看的自然语言说明） */
    String description() default "";
}
