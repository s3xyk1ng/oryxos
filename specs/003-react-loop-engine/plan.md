# Implementation Plan: ReAct 模块——Agent 引擎

**Branch**: `002-day2-react-refactor` | **Date**: 2026-10-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-react-loop-engine/spec.md`

## Summary

重构 OryxOS 的 ReAct 模块：将现有骨架代码改造为完整的 Reason-Act 循环引擎。核心改造包括 ReActLoop 对接 ProviderService（替换 LlmPort）、AgentService 入口编排（ProfileContext 生命周期）、PromptBuilder 四段拼接（含截断和时间戳）、ToolExecutor 全量审计（finally 覆盖）、ContextLoader 错误约束。同时清理 Day 1 过渡代码（LlmPort/LlmPortFactory/ToolCallParser 等），补齐数据模型（Message.toolCallId、ToolInvocationEntity.toolCallId）。

## Technical Context

**Language/Version**: JDK 21

**Primary Dependencies**: Spring Boot 3.x, Spring AI 1.0.0-M6 BOM, Spring AI Alibaba, Spring Data JPA

**Storage**: SQLite + Flyway（手工建表脚本，hibernate.ddl-auto=validate）

**Testing**: JUnit 5 + Mockito（单测），@Tag("integration") 冒烟测试 CI 跳过

**Target Platform**: 单机私有部署，可执行 JAR

**Project Type**: Agent OS 运行时内核（Spring Boot 单体应用）

**Performance Goals**: 同步阻塞 + Java 21 virtual thread，单机撑千级并发

**Constraints**: 禁止增强 switch（P3C/ASM 门禁），代码全英文注释用中文，凭证走环境变量

**Scale/Scope**: 23 个文件变动（改造 7 + 新建 4 + 删除 5 + 单测 6 + 模型变更 2）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

无 constitution 文件。基于 RULE.md 全局约定检查：

| 规则 | 状态 | 说明 |
|------|------|------|
| 代码全英文 | ✅ | 所有标识符、日志、异常消息用英文 |
| 注释用中文 | ✅ | Javadoc 和行注释用中文 |
| 传统 switch-case | ✅ | 禁止 `case ->` 增强 switch |
| 凭证不落明文 | ✅ | 环境变量占位 |

## Project Structure

### Documentation (this feature)

```text
specs/003-react-loop-engine/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
oryxos-core/
├── src/main/java/com/oryxos/core/
│   ├── agent/
│   │   ├── AgentService.java          # 改造：入口编排
│   │   ├── ProfileContext.java         # 新建：ThreadLocal
│   │   ├── AgentConfigException.java   # 新建：配置异常
│   │   └── ProviderService.java        # Day 1 已有
│   ├── react/
│   │   ├── ReActLoop.java             # 重写：循环引擎
│   │   ├── PromptBuilder.java         # 改造：四段拼接
│   │   └── ToolExecutor.java          # 改造：执行+审计
│   ├── context/
│   │   └── ContextLoader.java         # 改造：错误约束
│   └── model/
│       ├── Message.java               # 改造：加 toolCallId
│       ├── Profile.java               # 确认字段
│       └── ...                        # Day 1 已有

oryxos-storage/
├── src/main/java/com/oryxos/storage/
│   ├── entity/
│   │   └── ToolInvocationEntity.java  # 改造：加 toolCallId
│   └── repository/
│       └── ToolInvocationRepository.java  # 改造：加 findByToolCallId
└── src/main/resources/db/migration/sqlite/
    └── V5__add_tool_call_id.sql       # 新建

oryxos-core/src/test/java/com/oryxos/core/
├── react/
│   ├── ReActLoopTest.java             # 新建
│   ├── PromptBuilderTest.java         # 新建
│   └── ToolExecutorTest.java          # 新建
├── agent/
│   ├── AgentServiceTest.java          # 新建
│   └── ProfileContextTest.java        # 新建
└── context/
    └── ContextLoaderTest.java         # 新建

删除文件：
├── oryxos-core/.../react/LlmPort.java
├── oryxos-core/.../agent/LlmPortFactory.java
├── oryxos-provider/.../ProviderLlmPortFactory.java
├── oryxos-core/.../react/ToolCallParser.java
└── oryxos-core/.../model/ToolCallRequest.java
```

**Structure Decision**: 保持现有 Maven 多模块结构不变。ReAct 相关类在 oryxos-core，持久化实体在 oryxos-storage。
