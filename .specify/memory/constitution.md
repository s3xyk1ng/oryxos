<!--
同步影响报告
============
版本变更：（空模板）→ 1.0.0
修改的原则：无（首次批准）
新增章节：
  - 7 条核心原则（I–VII）
  - 技术栈约束
  - 开发工作流
  - 治理
删除章节：无
遗留 TODO：无
-->

# OryxOS 项目宪法

## 核心原则

### I. 自实现 ReAct Loop

ReAct 循环（`ReActLoop`、`PromptBuilder`、`ToolExecutor`）**必须**从零自己实现。**禁止**使用 Spring AI 的 Agent 抽象（`ChatClient.call` 的自动 tool 执行、`Advisor` 链式调用等）。

- 核心循环约数十行 Java 代码，位于 `oryxos-core` 模块。
- `ReActLoop` 驱动流程：组装 Prompt → 调用 LLM → 解析 Tool 调用 → 执行 Tool → 追加结果 → 循环。
- `MAX_ITERATIONS`（默认 10 次）**必须**强制执行，防止 Agent 陷入 Tool 调用死循环；可在 Profile 中覆盖。

**理由**：Agent 大脑的完全可控性是所有未来定制（自定义循环行为、多 Agent 委托、流式输出）的基础。依赖 Spring AI 的自动执行会让循环变得不透明且不可控。

### II. Spring AI 边界——只用一半，禁用自动执行

Spring AI 在 OryxOS 中只做两件事：

1. **Provider 抽象 + 协议转换**：`ChatClient` 封装 LLM 调用，完成与各家 LLM 协议的互转（OpenAI、Anthropic、Gemini 等）。
2. **`@Tool` 注解的 JSON Schema 生成**：Spring AI 扫描 `@Tool` 注解方法，生成工具描述 Schema。

Spring AI 的自动 Tool 执行机制**必须禁用**。Tool 的实际调度和执行完全由 OryxOS 自己的 `ReActLoop` + `ToolExecutor` 控制。

**理由**：如果 Spring AI 自动执行被意外启用，每个 Tool 会被调用两次——一次被 Spring AI，一次被 `ToolExecutor`。这是 OryxOS 开发中最容易踩的 Bug。

### III. 同步执行 + Virtual Threads

核心执行模型**必须**是同步阻塞，配合 JDK 21 virtual thread 实现高并发。

- 核心阶段**禁止**使用响应式编程（Reactor / WebFlux）。
- 每个并发请求运行在独立虚拟线程上，OS 线程维持几十个即可支撑数千并发。
- 扩展阶段引入 SSE 流式输出时使用 Spring MVC 的 `SseEmitter`，不用 WebFlux。

**理由**：同步代码直观、易调试。Virtual thread 提供高并发能力而无需响应式编程的复杂度。这保证了代码对企业 Java 开发者的可接近性。

### IV. OryxTool 抽象——统一 Tool 接口

所有工具——内置 Tool、`@Tool` 注解的 Plugin Tool、MCP Tool——**必须**包装为 `OryxTool` 实例注册到 `ToolRegistry`。ReAct 循环**不得**感知 Tool 的来源。

`OryxTool` 接口约定：
- `getName()`、`getDescription()`、`getInputSchema()`（JSON Schema）、`execute(JsonInput) → ToolResult`
- `ToolResult` 包含：成功标识、结果内容、错误信息、是否可重试。

**理由**：统一的 Tool 接口意味着 ReAct 循环代码不需要按 Tool 类型分支。新增 Tool 来源（新 MCP server、新注解风格）只需新增适配器，不改核心循环。

### V. Plugin Tool 三档接入

业务方扩展 OryxOS 工具能力分三档，门槛从低到高：

| 方式 | 门槛 | 做法 | 推荐度 |
|------|------|------|--------|
| 零代码 | 最低 | 写 Agent 目录（`AGENT.md`）+ 复用社区 MCP server | ⭐⭐⭐ 主推 |
| 轻代码 | 中等 | 用任何语言写 MCP server | ⭐⭐ |
| 重代码 | 最高 | 用 `@Tool` 注解写 Java Spring Bean | ⭐ |

**选择原则**：能用方式一就不用方式二，能用方式二就不用方式三。

**理由**：主要目标用户是写 Markdown、配置 MCP server 的业务团队，不是 Java 开发者。三档体系确保默认路径足够简单，同时为高级用户保留深度。

### VI. 单二进制部署

OryxOS **必须**可以单个可执行 JAR 部署（`java -jar`）。扩展阶段引入 GraalVM Native Image 进一步压缩启动时间和内存占用。

- 所有敏感配置（API key、凭证）**必须**通过环境变量或本地配置文件注入，**禁止**硬编码。
- `AGENT.md` frontmatter 中的 `${ENV_VAR}` 占位符由 `ConfigLoader` 在加载时解析。

**理由**：企业部署要求简洁——"装好就跑"，除 JDK 和 LLM API 端点外无外部依赖。单二进制也为扩展阶段的 GraalVM Native Image 铺路。

### VII. 审计 Day One

`tool_invocations` 和 `llm_calls` 两张审计表**必须**从核心阶段第一天就开始写入，不能只放日志。

- 每次 Tool 调用写一行到 `tool_invocations`（Tool 名称、输入 JSON、结果 JSON、是否成功、耗时）。
- 每次 LLM 调用写一行到 `llm_calls`（Provider、模型、输入 token、输出 token、耗时）。
- 这两张表的查询 API 放扩展阶段；但**写入**必须在核心阶段完成。

**理由**："可审计"是 OryxOS 面向严监管企业的核心差异化。如果审计数据只在日志里，后期做结构化审计需要从日志反解析——脆弱且昂贵。Day one 落表让审计地基成为永久资产。

## 技术栈约束

### 锁定技术栈

| 技术 | 版本 / 说明 |
|------|------------|
| JDK | 21+（virtual thread；不使用 SecurityManager） |
| Spring Boot | 3.x |
| Spring AI + Spring AI Alibaba | LLM Provider 抽象，复用主流 LLM connector |
| ReAct loop | 自实现，位于 `oryxos-core` |
| HTTP 服务层 | Spring MVC + virtual thread |
| 命令行 | Picocli |
| YAML 解析 | SnakeYAML |
| 持久化 | SQLite + Spring Data JPA + Flyway |
| 长期记忆 | `MEMORY.md` 文件 + 关键词检索 |
| MCP 集成 | MCP Java SDK（社区项目） |
| 日志 | Logback + SLF4J（结构化 JSON） |
| 监控 | Micrometer + Prometheus（扩展阶段） |

### 核心阶段禁用清单

- **禁止** Spring AI 自动 Tool 执行（自动执行链）
- **禁止** Reactor / WebFlux（响应式编程）
- **禁止** Java SecurityManager（JDK 17 起废弃，JDK 21 已不可用）
- **禁止** 核心阶段引入外部向量数据库进程（Qdrant、Chroma、Milvus 等）
- **禁止** 核心阶段实现认证机制（假设内网部署）

## 开发工作流

### 模块边界

OryxOS 是 Maven 多模块项目（14 个模块）。以下边界**必须**保持：

- `oryxos-core`：所有核心抽象（`OryxTool`、`Profile`、`Session`、`ReActLoop`、`PromptBuilder`、`ToolExecutor`、`AgentService`、`AgentLoader`、`ContextLoader`）。
- `oryxos-tool`：内置 Tool、MCP Client、`ToolRegistry`、`Sandbox` 的**合并模块**。**禁止**拆分为多个模块。
- `oryxos-provider`：仅负责 LLM Provider 抽象。
- `oryxos-memory`：`MemoryService` 统一门面、`LongTermMemoryStore`、`MemoryTools`。Memory 是统一门面，不横跨多个模块。
- Agent 目录（`AGENT.md`）**不是** Tool。它是 prompt 来源，由 `oryxos-core` 中的 `ContextLoader` 加载。

### Provider 显式映射

Provider 名称到 `ChatModel` 的映射**必须**是显式的（按名称配置：`deepseek`、`qwen`、`kimi` 等）。**禁止**通过类型扫描 Spring 容器中的 `ChatModel` Bean——多个同类型 Provider 会产生歧义。

### Sandbox 接口先行

`Sandbox` 接口（`enforce(SandboxAction)`）**必须**独立于任何实现定义。核心阶段只实现 `WhitelistSandbox`（应用层白名单）。扩展阶段新增容器/microVM 实现时不修改接口。

### Memory 门面模式

`MemoryService` 是 ReAct 循环访问所有记忆的唯一入口。内部委托给 `SessionManager`（会话记忆）和 `LongTermMemoryStore`（长期记忆）。三档后端（Markdown/SQLite/Mem0）通过配置切换，调用方代码零改动。

### 提交纪律

- 每个 User Story 完成**必须**标记一个 git commit。
- 实现过程中发现的宪法违反**必须**在合并前修复。

## 治理

本宪法是 OryxOS 项目的最高开发准则，所有 spec、plan、task 和实现产物都**必须**遵守。

### 修订流程

1. 通过 Pull Request 提出对本文件的修改。
2. 提供修改理由。
3. 7 条核心原则的任何修改需要一致同意。
4. 删除或重新定义原则需要 MAJOR 版本号递增。
5. 新增或实质性扩展原则需要 MINOR 版本号递增。
6. 措辞澄清和修正需要 PATCH 版本号递增。

### 合规审查

- 每个 PR **必须**对照本宪法检查合规性。
- AI Agent 检测到偏离时**必须**被指示重读本宪法。
- 每个 User Story 完成后**必须**运行 `/speckit-analyze` 验证 spec 与代码的一致性。

### 常见陷阱速查

| 陷阱 | 正确做法 |
|------|---------|
| 使用非 JDK 21 特性 | 强制 JDK 21 |
| 启用 Spring AI 自动 Tool 执行 | 必须禁用 |
| 将 `oryxos-tool` 拆成多个模块 | 保持为单一合并模块 |
| Provider 用类型扫描 | 必须用显式名称映射 |
| 把 `AGENT.md` 当作 Tool | 它是 `ContextLoader` 的 prompt 来源 |
| 跳过审计表写入 | `tool_invocations` + `llm_calls` day one 落库 |

**版本**：1.0.0 | **批准日期**：2026-10-09 | **最后修订**：2026-10-09
