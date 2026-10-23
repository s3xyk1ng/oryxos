# Implementation Plan: Tool 模块重构

**Branch**: `005-day5-tool-refactor` | **Date**: 2026-10-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-tool-refactor/spec.md`

## Summary

重构 Tool 模块四个核心问题：Sandbox 校验从 ToolExecutor 下沉到每个 Java 工具自身（工具自治）；新增 ConditionalTool 接口实现条件注册；新增 @Tool 注解体系支持 Java 开发者快速扩展工具；工具执行失败不拖垮 ReAct 循环（失败隔离）。

## Technical Context

**Language/Version**: JDK 21

**Primary Dependencies**: Spring Boot 3.x + Spring AI Alibaba + Jackson + JDK HttpClient

**Storage**: SQLite + Spring Data JPA（手工建表脚本）

**Testing**: JUnit 5 + Mockito + JDK HttpServer（mock）

**Target Platform**: 本地 JVM（CLI + Web Service）

**Project Type**: Maven 多模块项目（oryxos-core / oryxos-tool / ...）

**Performance Goals**: 工具执行超时不阻塞 ReAct 循环（maxIterations 兜底）

**Constraints**: 传统 switch-case（P3C/ASM 限制）；代码全英文注释用中文；mvn clean verify 全绿

**Scale/Scope**: 12 个生产文件变更 + 12 个测试类（51 用例）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

无 constitution 文件，跳过门禁检查。

## Project Structure

### Documentation (this feature)

```text
specs/003-tool-refactor/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
oryxos-core/src/main/java/com/oryxos/core/
├── model/
│   ├── ConditionalTool.java          # 新增：条件注册接口
│   ├── OryxTool.java                 # 不变
│   ├── ToolResult.java               # 不变
│   ├── Sandbox.java                  # 不变
│   └── SandboxAction.java            # 不变
├── agent/
│   └── ToolRegistry.java             # 不变
└── react/
    ├── ToolExecutor.java             # 改：删除 performSandboxCheck
    └── ReActLoop.java                # 改：retryable 信号 + 失败隔离

oryxos-tool/src/main/java/com/oryxos/tool/
├── ToolAutoRegistrar.java            # 改：条件注册
├── builtin/
│   ├── FileTools.java                # 改：注入 Sandbox
│   ├── ShellTools.java               # 改：注入 Sandbox
│   └── HttpTools.java                # 改：注入 Sandbox
├── notify/
│   ├── NotifyTool.java               # 新增：独立 @Component + ConditionalTool
│   └── NotifyTools.java              # 删除
├── annotation/
│   └── Tool.java                     # 新增：@Tool 注解
└── scanner/
    ├── ToolMethodScanner.java        # 新增：扫描 @Tool 方法
    └── AnnotatedToolAdapter.java     # 新增：适配 @Tool → OryxTool
```

**Structure Decision**: 保持现有 Maven 多模块结构。ConditionalTool 接口放 oryxos-core（被 ToolAutoRegistrar 和 NotifyTool 共同依赖）；其余实现放 oryxos-tool。
