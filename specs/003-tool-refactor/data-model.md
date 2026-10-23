# Data Model: Tool 模块重构

**Date**: 2026-10-23

## 无新增实体

本次重构不新增数据表或实体。所有变更均为接口层和逻辑层。

## 新增接口

### ConditionalTool

```java
package com.oryxos.core.model;

/**
 * 条件注册接口。实现此接口的工具，在 isAvailable() 返回 false 时不注册到 ToolRegistry。
 */
public interface ConditionalTool {
    boolean isAvailable();
}
```

### @Tool 注解

```java
package com.oryxos.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Spring Bean 方法上，框架启动时自动扫描并注册为 OryxTool。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {
    String name();
    String description() default "";
}
```

## 变更的类

| 类 | 变更类型 | 说明 |
|----|---------|------|
| ToolExecutor | 修改 | 删除 performSandboxCheck()；ReActLoop 拼接 retryable 信号 |
| ReActLoop | 修改 | for 循环加 try-catch 防御；失败信息拼接 [retryable]/[not retryable] |
| ToolAutoRegistrar | 修改 | 注册时检查 ConditionalTool.isAvailable() |
| FileTools | 修改 | 三个内部类各注入 Sandbox，execute() 第一行 enforce() |
| ShellTools | 修改 | 注入 Sandbox，execute() 第一行 enforce() |
| HttpTools | 修改 | 两个内部类各注入 Sandbox，execute() 第一行 enforce() |
| NotifyTool | 新增 | 独立 @Component，实现 OryxTool + ConditionalTool |
| NotifyTools | 删除 | 职责已拆分 |
| ToolMethodScanner | 新增 | 扫描 @Tool 方法，注册 AnnotatedToolAdapter |
| AnnotatedToolAdapter | 新增 | 适配 @Tool 方法为 OryxTool |
