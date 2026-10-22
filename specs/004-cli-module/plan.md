# Implementation Plan: CLI Module

**Branch**: `004-cli-module` | **Date**: 2026-10-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-cli-module/spec.md`

## Summary

CLI 模块是 OryxOS 的用户交互入口，解决"用户怎么跟 Agent 说话"的问题。基于 Picocli 4.7.6 实现 11 个命令，分轻重两类：轻命令不启动 Spring（文件 I/O，毫秒级响应），重命令显式声明 scanBasePackages 并统一做 Bean 断言 + 数据访问层读写验证。SessionManager 保持内存版，核心阶段不持久化。

## Technical Context

**Language/Version**: JDK 21

**Primary Dependencies**: 
- Picocli 4.7.6（命令行解析）
- Spring Boot 3.x（重命令启动）
- Spring AI Alibaba（LLM Provider 抽象，BOM 锁定）
- SQLite + Spring Data JPA + Flyway（数据访问层）

**Storage**: SQLite（sessions 表已存在，本次 SessionManager 保持内存版）

**Testing**: JUnit 5 + Mockito + Spring Boot Test

**Target Platform**: Windows/Linux/macOS（跨平台 CLI）

**Project Type**: CLI（命令行应用）

**Performance Goals**: 
- 轻命令响应 < 100ms
- 重命令 Spring 启动 < 5s

**Constraints**: 
- 避开 P3C/ASM 解析不了的 Java 18+ 语法（如增强 switch 的 default -> 写法）
- 静态检查是构建门禁（Spotless/Checkstyle/SpotBugs/PMD）

**Scale/Scope**: 11 个命令，8 个测试类，18 个验收点

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 状态 | 说明 |
|------|------|------|
| I. 自实现 ReAct Loop | ✅ 通过 | CLI 不碰 ReAct 循环，只调 AgentService |
| II. Spring AI 边界 | ✅ 通过 | CLI 不调用 Spring AI 自动执行 |
| III. 同步执行 + Virtual Threads | ✅ 通过 | CLI 用同步 Scanner 读 stdin |
| IV. OryxTool 抽象 | ✅ 通过 | CLI 不直接操作 Tool，调 AgentService |
| V. Plugin Tool 三档接入 | ✅ 通过 | CLI 不涉及 Tool 注册 |
| VI. 单二进制部署 | ✅ 通过 | CLI 是 oryxos-cli 模块，最终打包进单 JAR |
| VII. 审计 Day One | ✅ 通过 | CLI 调 AgentService，审计由 ReAct 层保证 |

**模块边界检查**：
- ✅ CLI 命令类全在 oryxos-cli
- ✅ SessionManager 保持内存版，在 oryxos-core
- ✅ Session 实体 + Repository 在 oryxos-storage（已有）
- ✅ 不新建 oryxos-channel-cli 模块（核心阶段不需要）

**无违规，无需 Complexity Tracking。**

## Project Structure

### Documentation (this feature)

```text
specs/004-cli-module/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── cli-commands.md  # 命令接口契约
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
oryxos-cli/
├── src/main/java/com/oryxos/cli/
│   ├── OryxOsCli.java                    # 主入口，轻重命令路由
│   ├── StartupVerifier.java              # 重命令启动验证
│   └── command/
│       ├── ChatCommand.java              # 交互式对话
│       ├── ServeCommand.java             # Web 服务启动
│       ├── GatewayCommand.java           # 守护进程
│       ├── InitCommand.java              # 初始化工作区
│       ├── StatusCommand.java            # 系统总览
│       ├── ProfileCommand.java           # Profile 命令组
│       ├── subcommand/
│       │   ├── ProfileListCommand.java
│       │   ├── ProfileCreateCommand.java
│       │   ├── ProfileShowCommand.java
│       │   └── ProfileDeleteCommand.java
│       ├── ProviderCommand.java          # Provider 命令组
│       ├── subcommand/
│       │   └── ProviderListCommand.java
│       ├── ToolCommand.java              # Tool 命令组
│       └── subcommand/
│           └── ToolListCommand.java
└── src/test/java/com/oryxos/cli/
    ├── command/
    │   ├── ProfileCommandTest.java       # 4 点
    │   ├── ProviderCommandTest.java      # 2 点
    │   ├── ToolCommandTest.java          # 2 点
    │   ├── StatusCommandTest.java        # 1 点
    │   ├── InitCommandTest.java          # 2 点
    │   └── ChatCommandTest.java          # 4 点
    └── StartupVerifierTest.java          # 重命令验证

oryxos-core/
└── src/main/java/com/oryxos/core/agent/
    └── SessionManager.java               # 内存版，ConcurrentHashMap

oryxos-storage/
├── src/main/java/com/oryxos/storage/
│   ├── entity/
│   │   └── SessionEntity.java            # 已有
│   └── repository/
│       └── SessionRepository.java        # 已有
└── src/main/resources/db/migration/sqlite/
    └── V1__init_schema.sql               # sessions 表已创建
```

**Structure Decision**: 保持 oryxos-cli 单模块，不拆 channel-cli。所有命令类在 `com.oryxos.cli.command` 包下，子命令用 `subcommand` 子包。
