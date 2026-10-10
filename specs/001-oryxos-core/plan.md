# Implementation Plan: OryxOS 核心运行时内核

**Branch**: `001-oryxos-core` | **Date**: 2026-10-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-oryxos-core/spec.md`

## Summary

OryxOS 核心阶段交付 Agent OS 的运行时内核，实现 5 大核心能力：Provider 抽象（对接 LLM）、ReAct 循环（Agent 大脑）、Memory 三层记忆、Plugin Tool 体系、Web Service（REST API）。技术栈为 JDK 21 + Spring Boot 3.x + Spring AI Alibaba，自实现 ReAct loop，SQLite 持久化，14 个 Maven 模块，单二进制部署。5 个 User Story 按依赖关系推进：US-1 → US-2 → (US-3 ‖ US-4) → US-5。

## Technical Context

**Language/Version**: JDK 21

**Primary Dependencies**: Spring Boot 3.x, Spring AI + Spring AI Alibaba 1.0.0-M6.1, Picocli, SnakeYAML, Spring Data JPA, SQLite JDBC, MCP Java SDK, Logback + SLF4J

**Storage**: SQLite（Session、审计表、定时任务状态）+ `MEMORY.md` 文件（长期记忆）+ 文件系统（Agent 目录、Bootstrap、Skill 库）

**Testing**: JUnit 5 + Spring Boot Test + Mockito

**Target Platform**: Linux 主流发行版（Ubuntu 22.04+、CentOS 8+、Debian 11+），Windows/macOS 开发环境

**Project Type**: CLI + Web Service（单体应用，三种运行模式：`chat`/`serve`/`gateway`）

**Performance Goals**: 单节点 ≥10 Agent、≥100 并发 Session、Session 创建 P99 ≤200ms、内部转发开销 ≤50ms

**Constraints**: 单二进制部署、同步执行 + Virtual Threads、应用层白名单 Sandbox、核心阶段无认证

**Scale/Scope**: 14 个 Maven 模块、30 条功能需求、5 个 User Story、3 个端到端验收 Demo

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | 原则 | 合规状态 | 说明 |
|---|------|---------|------|
| I | 自实现 ReAct Loop | ✅ PASS | `ReActLoop`/`PromptBuilder`/`ToolExecutor` 在 `oryxos-core` 自实现，不使用 Spring AI Agent 抽象 |
| II | Spring AI 只用一半 | ✅ PASS | 仅用 Provider 抽象 + 协议转换 + `@Tool` Schema 生成；禁用自动 tool 执行 |
| III | 同步执行 + Virtual Threads | ✅ PASS | Spring MVC + Virtual Threads，不使用 Reactor/WebFlux |
| IV | OryxTool 统一接口 | ✅ PASS | 内置/MCP/@Tool 统一包装为 `OryxTool`，注册到 `ToolRegistry` |
| V | Plugin Tool 三档 | ✅ PASS | 零代码 AGENT.md+MCP / 轻代码 MCP server / 重代码 @Tool Bean |
| VI | 单二进制部署 | ✅ PASS | 可执行 JAR，`java -jar` 启动 |
| VII | 审计 Day One | ✅ PASS | `tool_invocations` + `llm_calls` 核心阶段写入 |

**附加检查**：

| 检查项 | 状态 | 说明 |
|--------|------|------|
| Provider 显式映射 | ✅ PASS | provider name → ChatModel 显式映射，不靠类型扫描 |
| Sandbox 接口先行 | ✅ PASS | `Sandbox` 接口独立于实现，核心阶段 `WhitelistSandbox` |
| Memory 统一门面 | ✅ PASS | `MemoryService` 单入口，内部分约会话/长期记忆 |
| 模块边界 | ✅ PASS | `oryxos-tool` 三合一不拆分，Agent 目录归 `oryxos-core` |
| MCP 双协议 | ✅ PASS | stdio + SSE 两种传输（clarify 决定） |
| 定时任务完整实现 | ✅ PASS | 状态持久化 + 执行历史 + 管理端点（clarify 决定） |

**GATE 结果**: ✅ ALL PASS — 无违规，进入 Phase 0。

## Project Structure

### Documentation (this feature)

```text
specs/001-oryxos-core/
├── plan.md              # 本文件
├── spec.md              # 功能规格（/speckit-specify 产出）
├── research.md          # Phase 0 研究产出
├── data-model.md        # Phase 1 数据模型
├── quickstart.md        # Phase 1 验证指南
├── contracts/           # Phase 1 接口契约
│   └── rest-api.md      # REST API 端点契约
├── checklists/
│   └── requirements.md  # 规格质量检查清单
└── tasks.md             # Phase 2 任务拆解（/speckit-tasks 产出）
```

### Source Code (repository root)

```text
oryxos/
├── pom.xml                          # 父 POM
├── oryxos-core/                     # 核心抽象和接口
│   └── src/main/java/com/oryxos/core/
│       ├── model/                   # Profile, Session, Message, OryxTool, ToolResult
│       ├── agent/                   # AgentLoader, AgentService, AgentScheduler
│       ├── react/                   # ReActLoop, PromptBuilder, ToolExecutor
│       ├── context/                 # ContextLoader（Bootstrap + AGENT.md + Skill）
│       ├── memory/                  # MemoryService 门面接口
│       ├── channel/                 # InboundChannelAdapter 契约
│       └── knowledge/               # KnowledgeService 契约
├── oryxos-persona/                  # 人格库（12 内置预设 + 自定义 CRUD）
├── oryxos-provider/                 # Provider 抽象 + Function Calling 适配
│   └── src/main/java/com/oryxos/provider/
│       ├── ProviderService.java
│       └── FunctionCallingAdapter.java
├── oryxos-memory/                   # Memory 实现
│   └── src/main/java/com/oryxos/memory/
│       ├── MemoryServiceImpl.java
│       ├── LongTermMemoryStore.java # 可插拔后端接口
│       ├── MarkdownMemoryStore.java # 默认：MEMORY.md
│       ├── SqliteMemoryStore.java   # SQLite 档
│       └── MemoryTools.java         # save_memory, recall_memory
├── oryxos-knowledge/                # 知识库（解析/切分/检索）
├── oryxos-tool/                     # Tool 体系（三合一）
│   └── src/main/java/com/oryxos/tool/
│       ├── builtin/                 # FileTools, ShellTools, HttpTools, NotifyTools
│       ├── mcp/                     # McpClientService, McpToolAdapter
│       ├── sandbox/                 # Sandbox 接口, WhitelistSandbox
│       ├── notify/                  # NotifyChannelAdapter, WebhookNotifyAdapter
│       └── ToolRegistry.java
├── oryxos-channel-cli/              # CLI Channel
├── oryxos-channel-feishu/           # 飞书入站渠道
├── oryxos-channel-wecom/            # 企微入站渠道
├── oryxos-channel-dingtalk/         # 钉钉入站渠道
├── oryxos-web/                      # REST API
│   └── src/main/java/com/oryxos/web/
│       ├── controller/              # 6 个 ApiController
│       ├── exception/               # GlobalExceptionHandler
│       └── model/                   # ApiResponse 信封
├── oryxos-storage/                  # 持久化层
│   └── src/main/java/com/oryxos/storage/
│       ├── entity/                  # JPA 实体
│       ├── repository/              # Spring Data JPA Repository
│       └── db/migration/            # Flyway 迁移脚本
├── oryxos-cli/                      # 命令行入口（Picocli）
│   └── src/main/java/com/oryxos/cli/
│       ├── OryxOsCli.java
│       └── command/                 # 子命令
├── oryxos-boot/                     # Spring Boot 启动
│   └── src/main/java/com/oryxos/boot/
│       └── OryxOsApplication.java
└── docs/                            # 项目文档
    ├── DemandAnalysis.md
    ├── TechnicalSolution.md
    ├── IndustryResearch.md
    └── AiProgrammingGuide.md
```

**Structure Decision**: Maven 多模块单体项目，14 个模块按职责划分。`oryxos-cli` 为唯一可执行入口（依赖 `oryxos-boot`），通过 `spring-boot-maven-plugin` 打包 fat JAR。模块间通过接口解耦，扩展阶段加新 Channel 或 Tool 只加新模块不改 core。

## Complexity Tracking

> 无宪法违规，此表为空。
