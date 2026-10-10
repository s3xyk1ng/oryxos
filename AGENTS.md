# OryxOS — 项目级 Agent 行为说明

你正在参与 **OryxOS** 的开发——一个用 Java 实现的企业级 Agent OS。OryxOS 是 AI Agent 的统一运行时底座：它部署在企业自有基础设施上，向上为各类业务 Agent（运维助手、客服助手、HR 助手、销售助手等）提供共享的运行环境，向下接入模型、渠道、工具、记忆和审计基础设施。Agent 是配置出来的，不是写代码写出来的。

---

## 项目定位

- **是什么**：Java 原生、私有可部署、可审计的 Agent 统一底座
- **服务对象**：严监管企业（银行、政府、电信、能源、医疗），需要完全掌控 AI Agent 基础设施
- **核心价值**：统一、私有、易接入、可观测
- **边界**：守住运行时这一层，不往上做编排。与 Dify 等编排平台是互补关系，与 Spring AI 等框架是复用关系

---

## 不可协商原则

以下原则在任何代码变更中都必须遵守，违反即为 critical，必须在合并前修复。

### 1. 自实现 ReAct Loop

ReAct 循环（`ReActLoop`、`PromptBuilder`、`ToolExecutor`）从零自己实现。**禁止**使用 Spring AI 的 Agent 抽象（`ChatClient.call` 的自动 tool 执行、`Advisor` 链式调用等）。Agent 核心循环完全可控，保留定制空间。

### 2. Spring AI 边界——只用一半

Spring AI 在 OryxOS 里只做两件事：
- Provider 抽象 + 向各家 LLM 的协议转换
- `@Tool` 注解的 JSON Schema 生成

**禁止启用 Spring AI 的自动 tool 执行机制**。Tool 的实际调度和执行完全由 OryxOS 的 `ReActLoop` + `ToolExecutor` 控制。违反此条会导致 tool 被调两次。这是最容易被写错的一条原则。

### 3. 同步执行 + Virtual Threads

核心阶段采用同步阻塞执行模型，配合 JDK 21 virtual thread。不使用响应式编程（Reactor/WebFly）。单节点支撑高并发不需要响应式。扩展阶段引入 SSE 流式输出时使用 `SseEmitter`。

### 4. OryxTool 抽象——统一 Tool 接口

内置 Tool、`@Tool` 注解的 Plugin Tool、MCP Tool 都被包装成 `OryxTool` 实例注册到 `ToolRegistry`。ReAct 循环不感知 Tool 来源。`OryxTool` 接口约定：`getName`、`getDescription`、`getInputSchema`（JSON Schema）、`execute`（接收 JSON 输入返回 `ToolResult`）。

### 5. Plugin Tool 三档接入

| 方式 | 门槛 | 做法 | 推荐度 |
|------|------|------|--------|
| 零代码 | 最低 | 写 Agent 目录（`AGENT.md`）+ 复用社区 MCP server | ⭐⭐⭐ 主推 |
| 轻代码 | 中等 | 用任何语言写 MCP server | ⭐⭐ |
| 重代码 | 最高 | 用 `@Tool` 注解写 Java Spring Bean | ⭐ |

选择原则：能用方式一就不用方式二，能用方式二就不用方式三。

### 6. 单二进制部署

OryxOS 是一个可执行 JAR，`java -jar` 启动。扩展阶段引入 GraalVM Native Image 进一步压缩启动时间和内存。所有配置通过环境变量或本地文件注入，不明文硬编码。

### 7. 审计 Day One

`tool_invocations` 和 `llm_calls` 两张审计表在核心阶段就做写入，不是只放日志。可审计是 OryxOS 的差异化卖点，数据地基 day one 就立起来，避免后期从日志反解析返工。

---

## 技术栈约束

### 锁定技术栈

| 技术 | 版本/说明 |
|------|----------|
| JDK | 21+（virtual thread、不使用 SecurityManager） |
| Spring Boot | 3.x |
| Spring AI + Spring AI Alibaba | LLM Provider 抽象，复用主流 LLM connector |
| ReAct loop | 自实现，不依赖 Spring AI Agent 抽象 |
| HTTP 服务层 | Spring MVC + virtual thread |
| 命令行 | Picocli |
| YAML 解析 | SnakeYAML |
| 持久化 | SQLite + Spring Data JPA + Flyway（`db/migration/{vendor}/` 双轨目录） |
| 长期记忆 | `MEMORY.md` 文件 + 关键词检索（三档后端：Markdown/SQLite/Mem0） |
| MCP 集成 | MCP Java SDK（社区项目） |
| 日志 | Logback + SLF4J（结构化 JSON） |
| 监控 | Micrometer + Prometheus（扩展阶段） |

### 禁用清单

- **禁止** Spring AI 自动 tool 执行（`ChatClient` 的自动执行链）
- **禁止** Reactor / WebFlux（核心阶段）
- **禁止** Java SecurityManager（JDK 17 起废弃，JDK 21 不可用）
- **禁止** 核心阶段引入向量数据库外部进程（Qdrant、Chroma、Milvus 等）
- **禁止** 核心阶段做认证机制（无认证假设内网）

---

## 模块结构

OryxOS 是 Maven 多模块项目，由 14 个模块组成：

| 模块 | 职责 |
|------|------|
| `oryxos-core` | 核心抽象和接口：`OryxTool`、`Session`、`Profile`（含 `Persona` 人格）、`ContextLoader`、`AgentLoader`、`ReActLoop`、`PromptBuilder`、`ToolExecutor`、`AgentService`、`AgentScheduler` |
| `oryxos-persona` | 人格库：12 个内置人格预设 + 自定义人格 CRUD |
| `oryxos-provider` | 核心能力一：`ProviderService`、Function Calling 适配、provider name 到 `ChatModel` 显式映射 |
| `oryxos-memory` | 核心能力三：`MemoryService` 统一门面、`LongTermMemoryStore` 可插拔后端、`MemoryTools` |
| `oryxos-knowledge` | 知识库：本地后端、解析/切分/向量化索引、双路召回 + RRF 融合、`KnowledgeTools` |
| `oryxos-tool` | 核心能力四：内置 Tool（File/Shell/Http/Notify）、MCP Client、`ToolRegistry`、`Sandbox` + `WhitelistSandbox`、`NotifyChannelAdapter`（三合一模块） |
| `oryxos-channel-cli` | CLI Channel：`oryxos chat` 命令 |
| `oryxos-channel-feishu` | 飞书入站渠道：长连接收消息、事件标准化、出站发送 |
| `oryxos-channel-wecom` | 企微入站渠道：长连接收消息、事件标准化、出站发送 |
| `oryxos-channel-dingtalk` | 钉钉入站渠道：Stream 长连接、事件标准化、出站发送 |
| `oryxos-web` | 核心能力五：`WebServer`、6 个 `ApiController`、`GlobalExceptionHandler`、OpenAPI |
| `oryxos-storage` | 持久化层：SQLite、JPA Repository、Flyway 迁移 |
| `oryxos-cli` | 命令行入口：Picocli 主入口、13 个子命令、`ConfigLoader` |
| `oryxos-boot` | Spring Boot 启动模块：主类、自动配置、依赖聚合 |

模块间通过接口解耦。扩展阶段加新 Channel 或新 Tool 实现只加新模块不改 core。

---

## 关键架构概念

### Agent 定义：一个目录 = 一个 Agent

`.oryxos/agents/<name>/` 目录包含：
- `AGENT.md`：frontmatter 是运行配置（Provider、Tool 列表、Channel、定时规则），正文是任务指令
- `skills/`：公共 Skill 的相对软连接，绑定可见 Skill 集合
- `scripts/`：可选脚本
- `REFERENCE.md`：可选参考文档

`AgentLoader.deriveProfile()` 把 `AGENT.md` frontmatter 派生成 `Profile`，让 Agent 目录零改动复用整台底座。

### 五大能力关系

- **ReAct 循环**是引擎，驱动整个 Agent 大脑
- **Provider** 给 ReAct 循环提供 LLM 调用能力
- **Memory** 给 ReAct 循环提供上下文（会话历史 + 长期记忆）
- **Tool** 给 ReAct 循环提供执行能力
- **Web Service** 是对外门面，把内部能力包装成 REST API

一句话：Provider、Memory、Tool 供养 ReAct 循环这个引擎，引擎跑出的能力通过 CLI、Web Service、定时任务三个入口对外提供。

### 三种触发源

| 触发源 | 入口 | 说明 |
|--------|------|------|
| CLI | `oryxos chat` | 人推，交互对话 |
| Web Service | `POST /api/v1/agents/{name}/invoke` | 人推，HTTP API |
| AgentScheduler | cron 表达式 | 钟推，定时自动触发 |

三种触发源共用同一个 `AgentService.process()` 链路，ReActLoop 不感知消息从哪个入口来。

### Sandbox 接口先行

先抽象 `Sandbox` 接口（`enforce(SandboxAction action)`），不携带任何实现细节。核心阶段只实现 `WhitelistSandbox`（应用层白名单），扩展阶段按信号驱动升级到容器隔离或 microVM，接口不变，只新增实现类。

### Memory 统一门面

`MemoryService` 对 ReAct 循环只暴露一个接口，内部分为会话记忆（`SessionManager`）和长期记忆（`LongTermMemoryStore`）。长期记忆三档后端通过配置切换：
- `MarkdownMemoryStore`（默认）：`.oryxos/memory/MEMORY.md`，零依赖、人可读
- `SqliteMemoryStore`：记忆入库，结构化查询
- `Mem0MemoryStore`：接自托管 Mem0 记忆层，语义检索

---

## 代码规范

- **JDK 21**：不使用更高版本的特性，不使用已废弃的 API
- **结构化日志**：所有关键操作（LLM 调用、Tool 调用、Session 变更）落结构化 JSON 日志
- **API 规范**：REST API 统一前缀 `/api/v1`，标准 HTTP 状态码，统一响应信封 `ApiResponse`（`code`、`message`、`data`、`timestamp`）
- **配置规范**：敏感配置通过 `${ENV_VAR}` 占位从环境变量注入，不明文硬编码
- **Sandbox 校验**：FileTools、ShellTools、HttpTools 执行前必须调用 `sandbox.enforce(...)`
- **审计写入**：每次 Tool 调用写 `tool_invocations`，每次 LLM 调用写 `llm_calls`
- **Provider 映射**：必须用显式 provider name 到 `ChatModel` 的映射，不靠类型扫描

---

## OryxOS 不是什么

| 不是 | 说明 |
|------|------|
| 不是编排平台 | 不做可视化 workflow 编排、复杂任务分解、多 Agent 显式协作 |
| 不是框架 | 产物不是代码/库/SDK，而是装好就跑的服务 |
| 不是 SaaS | 私有部署在企业自有基础设施，不绑任何云 |
| Agent 目录不是 Tool | `AGENT.md` 正文注入 system prompt，子资源经 `read_file`/`shell` 按需取用，不新造机制 |

---

## 验收 Demo

核心阶段完成后必须跑通三个端到端 Demo：

| Demo | 场景 | 验证能力 |
|------|------|---------|
| **每日天气** | 每天早上自动查天气、生成穿搭建议、推送到企业 IM 群 | Provider + ReAct + 内置 HTTP Tool + NotifyTools + Sandbox + 定时任务 |
| **每日科技日报** | 每天自动汇总科技新闻并推送，体现用户记忆偏好 | Agent 目录 + Skill 绑定 + MCP + Memory + 定时任务（全程零 Java 代码） |
| **每日 GitHub 日报** | 每天自动抓 GitHub 热门项目、做 AI 相关总结并推送 | Agent 目录 + scripts/ 脚本 + ReAct + Memory + NotifyTools + 定时任务 |

三个 Demo 都要同时支持"钟推"（定时自动）和"人推"（手动补跑），验证同一条 `AgentService` 链路。

---

## 工作区结构

```
.oryxos/
├── agents/            # 每个子目录 = 一个 Agent
├── skills/            # 公共 Skill 实体库
├── memory/
│   └── MEMORY.md      # 长期记忆
├── mcp_servers.yaml   # MCP 配置
├── sessions/          # Session 数据
├── logs/              # 日志
├── AGENTS.md          # Bootstrap：项目级 agent 行为说明（本文件）
├── SOUL.md            # Bootstrap：默认 agent 人格定义
├── USER.md            # Bootstrap：用户偏好
└── oryxos.db          # SQLite
```
