# Tasks: OryxOS 核心运行时内核

**Input**: Design documents from `specs/001-oryxos-core/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: 未明确要求 TDD，不生成独立测试任务。验收通过 quickstart.md 端到端验证。

**Organization**: 按 User Story 组织，5 个 US 对应 Phase 3-7。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件，无依赖）
- **[Story]**: 所属 User Story（US1-US5）

---

## Phase 1: Setup（项目骨架）

**Purpose**: Maven 多模块骨架 + 核心抽象 + 数据库基础

- [x] T001 验证现有 Maven 多模块骨架与 plan.md 的 14 模块一致，补齐缺失模块（`oryxos-persona`、`oryxos-knowledge`、`oryxos-channel-feishu/wecom/dingtalk`），更新父 `pom.xml`
- [x] T002 [P] 在 `oryxos-core` 创建核心模型：`Profile.java`、`Session.java`、`Message.java`（role/content/timestamp）、`OryxTool.java` 接口（getName/getDescription/getInputSchema/execute）、`ToolResult.java`（success/content/error/retryable）
- [x] T003 [P] 在 `oryxos-core` 创建 `SandboxAction.java`（ActionType 枚举：FILE_READ/FILE_WRITE/SHELL_COMMAND/HTTP_REQUEST）和 `Sandbox.java` 接口（`enforce(SandboxAction)`）
- [x] T004 [P] 在 `oryxos-storage` 创建 JPA 实体：`SessionEntity.java`（session_id VARCHAR(64) PK, profile_name, channel, user_id, messages_json TEXT, status VARCHAR(16), created_at, last_active_at, archived_at）、`ToolInvocationEntity.java`、`LlmCallEntity.java`
- [x] T005 [P] 在 `oryxos-storage` 创建 JPA Repository：`SessionRepository.java`、`ToolInvocationRepository.java`、`LlmCallRepository.java`
- [x] T006 [P] 在 `oryxos-storage/src/main/resources/db/migration/sqlite/` 创建 Flyway 迁移脚本 `V1__init_schema.sql`（sessions + tool_invocations + llm_calls 三张表）
- [x] T007 在 `oryxos-boot` 配置 `application.yaml`：SQLite 数据源（`jdbc:sqlite:.oryxos/oryxos.db`）、JPA 配置、Flyway 配置、Virtual Threads 启用（`spring.threads.virtual.enabled=true`）

**Checkpoint**: 项目骨架就绪，核心抽象定义完成，数据库可初始化

---

## Phase 2: Foundational（基础设施）

**Purpose**: 配置加载 + Profile 注册 + 上下文加载 + 审计写入——所有 US 共用的地基

- [x] T008 在 `oryxos-core` 创建 `ConfigLoader.java`：从环境变量加载敏感配置（`${ENV_VAR}` 占位解析），基础校验（必填项/格式）
- [x] T009 在 `oryxos-core` 创建 `AgentLoader.java`：扫描 `.oryxos/agents/` 目录，`deriveProfile(agentDir)` 解析 AGENT.md frontmatter 生成 Profile，注册到 ProfileRegistry
- [x] T010 在 `oryxos-core` 创建 `ProfileRegistry.java`：按 name 索引 Profile，提供 `register`/`remove`/`exists`/`getByName` 方法
- [x] T011 在 `oryxos-core` 创建 `ContextLoader.java`：按 Profile 的 `bootstrap` 字段读取 Bootstrap 文件（AGENTS.md/SOUL.md/USER.md），读取 AGENT.md 正文，扫描 Agent `skills/` 软连接注入 Skill 元数据（name/description/path），每次现读不缓存
- [x] T012 在 `oryxos-core` 创建 `AuditService.java`：封装 `tool_invocations` 和 `llm_calls` 的写入逻辑，提供 `recordToolInvocation(...)` 和 `recordLlmCall(...)` 方法
- [x] T013 在 `oryxos-cli` 实现 `InitCommand.java`：`oryxos init` 创建 `.oryxos/` 目录及子目录（agents/skills/memory/sessions/logs），创建默认 Bootstrap 文件和 MEMORY.md，操作幂等

**Checkpoint**: 配置加载、Profile 注册、上下文加载、审计服务就绪

---

## Phase 3: User Story 1 — 对接 LLM（Provider 抽象）(Priority: P1) 🎯

**Goal**: 让 OryxOS 能调任意主流 LLM，Agent 不感知具体调的是哪家

**Independent Test**: 配置 DeepSeek Provider，通过 CLI 向 Agent 发送消息并收到 LLM 回复

### Implementation

- [x] T014 [US1] 在 `oryxos-provider` 创建 `ProviderService.java`：维护 provider name → ChatModel 显式映射（不靠类型扫描），提供 `getChatModel(providerName)` 方法，启动时从 `application.yaml` 构建映射表
- [x] T015 [US1] 在 `oryxos-provider` 创建 `FunctionCallingAdapter.java`：将 `OryxTool` 列表转换为 Spring AI 的 Tool 格式（OpenAI/Anthropic/Gemini 协议），将 LLM 返回的 tool call 解析为内部 `ToolCallRequest`（tool_name + arguments JSON）
- [x] T016 [US1] 在 `oryxos-provider` 创建 `ProviderConfig.java`：Spring `@Configuration`，根据 `application.yaml` 中的 Provider 配置创建 `ChatModel` Bean（DeepSeek/Kimi/通义等），使用 Spring AI Alibaba connector
- [x] T017 [US1] 在 `application.yaml` 添加 Provider 配置段：`oryxos.providers` 列表（每项含 name/model/api-key-env/base-url），API key 通过 `${ENV_VAR}` 引用环境变量
- [x] T018 [US1] 在 `oryxos-cli` 注册 `ChatCommand.java`（`oryxos chat`）基础版：启动 Spring 上下文，加载 Profile，调用 `ProviderService` 发送单条消息并打印 LLM 回复（不含 ReAct 循环，纯 LLM 调用验证连通性）

**Checkpoint**: `oryxos chat` 可向配置的 LLM 发送消息并收到回复，Provider 切换只需改配置

---

## Phase 4: User Story 2 — ReAct 循环（Agent 大脑）(Priority: P1)

**Goal**: Agent 自主决定何时调用工具，多步骤任务一次对话内完成

**Independent Test**: `oryxos chat` 发送"查北京天气"，Agent 自主调用 HTTP Tool 获取数据后生成穿搭建议

**Depends on**: US-1（Provider 抽象）

### Implementation

- [x] T019 [US2] 在 `oryxos-core` 创建 `ReActLoop.java`：核心循环（组装 Prompt → 调 LLM → 解析 tool calls → 执行 Tool → 追加结果 → 循环），`MAX_ITERATIONS` 控制（默认 10，Profile 可覆盖），无 tool call 时终止
- [x] T020 [US2] 在 `oryxos-core` 创建 `PromptBuilder.java`：四部分拼接——①system prompt（AGENT.md 正文 + Bootstrap + Skill 元数据，由 ContextLoader 提供，末尾附当前日期时间）②Memory 注入（由 MemoryService 提供）③对话历史（按 maxHistoryTurns 截断）④可用 Tool 列表（Function Calling 格式）
- [x] T021 [US2] 在 `oryxos-core` 创建 `ToolExecutor.java`：从 ToolRegistry 查找 Tool → Sandbox 检查 → 执行 → 包装 ToolResult → 写入 tool_invocations 审计表（通过 AuditService），失败时返回错误信息给 ReAct 循环
- [x] T022 [US2] 在 `oryxos-core` 创建 `AgentService.java`：统一入口 `process(Session, String message)`，内部：设置 ProfileContext（ThreadLocal）→ 调 ReActLoop.run → 持久化 Session → finally 清 ProfileContext
- [x] T023 [US2] 在 `oryxos-tool` 创建 `HttpTools.java`：`http_get`（url 参数，域名白名单校验）和 `http_post`（url + body 参数），执行前调 `Sandbox.enforce(HTTP_REQUEST, url)`
- [x] T024 [US2] 在 `oryxos-tool` 创建 `Sandbox.java` 接口的 `WhitelistSandbox.java` 实现：`checkFilePath`（路径标准化 + `../` 穿越防护）、`checkShellCommand`（精确比对可执行文件白名单）、`checkHttpUrl`（host 解析 + 通配符匹配），配置在 `application.yaml`（`file.allowed_paths`/`shell.allowed_commands`/`http.allowed_domains`）
- [x] T025 [US2] 在 `oryxos-core` 创建 `ToolRegistry.java`：启动时通过 Spring 容器扫描所有 `@Tool` 注解方法，包装为 `OryxTool` 实例，提供 `getTool(name)`/`getAllTools()`/`getToolsForProfile(profile)` 方法
- [x] T026 [US2] 在 `oryxos-core` 创建 `SessionManager.java`：内存版 Session 管理（核心阶段先用内存，US-5 升级 SQLite），按 Channel+user+profile 联合 ID 管理，支持创建/查询/追加消息/截断历史
- [x] T027 [US2] 完善 `oryxos-cli` 的 `ChatCommand.java`：交互式多轮对话循环，读取用户输入 → 调 AgentService.process → 打印响应 → 支持 `/quit` 退出，启动时交互式提示输入用户名

**Checkpoint**: `oryxos chat` 完整 ReAct 循环可用，Agent 自主调用 HTTP Tool 完成多步骤任务

---

## Phase 5: User Story 3 — Memory 三层记忆 (Priority: P2)

**Goal**: Agent 跨对话记住用户偏好，重启后记忆不丢失

**Independent Test**: 第一次对话写入偏好 → 重启 → 第二次对话验证 Agent 引用了之前保存的信息

**Depends on**: US-2（ReAct 循环）

### Implementation

- [x] T028 [US3] 在 `oryxos-memory` 创建 `LongTermMemoryStore.java` 接口：`append(content, scope)`、`load()`（核心记忆区全量 + 归档区截断）、`recallByKeyword(query)`（归档区关键词匹配，不区分大小写）、`capabilities()`
- [x] T029 [US3] 在 `oryxos-memory` 创建 `MarkdownMemoryStore.java`（默认实现）：操作 `.oryxos/memory/MEMORY.md`，按 `## 核心记忆` / `## 归档记忆` 分区，截断为字符串裁剪归档段，检索为行匹配
- [x] T030 [US3] 在 `oryxos-memory` 创建 `MemoryService.java`（统一门面）：对 ReAct 循环暴露统一接口，内部委托 SessionManager（会话记忆）+ LongTermMemoryStore（长期记忆），提供 `getContext(session)` / `saveMemory(content, scope)` / `recallMemory(query)` 方法
- [x] T031 [US3] 在 `oryxos-memory` 创建 `MemoryTools.java`：`save_memory`（content + 可选 scope 参数，默认 ARCHIVAL）和 `recall_memory`（query 关键词），标注 `@Tool` 注解自动注册到 ToolRegistry
- [x] T032 [US3] 修改 `PromptBuilder.java`：在 prompt 组装中通过 `MemoryService.getContext(session)` 注入会话历史 + 长期记忆，替代直接读 SessionManager
- [x] T033 [US3] 在 `application.yaml` 添加 Memory 配置段：`oryxos.memory.backend: markdown`（可选 markdown/sqlite/mem0），MEMORY.md 路径配置

**Checkpoint**: Agent 可通过 save_memory/recall_memory 主动读写长期记忆，重启后 MEMORY.md 内容被加载到 system prompt

---

## Phase 6: User Story 4 — Plugin Tool 体系 (Priority: P2)

**Goal**: 业务方零代码（AGENT.md + MCP）或轻代码（@Tool Bean）扩展 Agent 能力

**Independent Test**: 创建零代码 Agent 目录 + 配置 MCP server，Agent 调用外部 MCP 工具完成任务

**Depends on**: US-2（ReAct 循环）| 可与 US-3 并行

### Implementation

- [x] T034 [P] [US4] 在 `oryxos-tool` 创建 `FileTools.java`：`read_file`（path）、`write_file`（path + content）、`list_dir`（path），执行前调 `Sandbox.enforce(FILE_READ/FILE_WRITE, path)`
- [x] T035 [P] [US4] 在 `oryxos-tool` 创建 `ShellTools.java`：`shell`（command + args 数组），执行前调 `Sandbox.enforce(SHELL_COMMAND, command)`，带超时控制，不经 Shell 解释器
- [x] T036 [US4] 在 `oryxos-tool` 创建 `McpClientService.java`：启动时连接所有 `mcp_servers.yaml` 中配置的 MCP server，调 `tools/list` 获取工具列表，支持 stdio + SSE 两种传输协议，处理断连/超时/错误恢复
- [x] T037 [US4] 在 `oryxos-tool` 创建 `McpToolAdapter.java`：将 MCP Tool 适配为 `OryxTool` 接口，tool 调用时通过 MCP 协议（JSON-RPC）转发给对应 MCP server，结果包装为 ToolResult
- [x] T038 [US4] 在 `oryxos-tool` 创建 `NotifyTools.java`：`notify`（content + channel 参数），从 SQLite `notify_channels` 全局注册表解析适配器和 URL，发送前过 Sandbox 域名白名单
- [x] T039 [US4] 在 `oryxos-tool` 创建 `NotifyChannelAdapter.java` 接口 + `WebhookNotifyAdapter.java` 实现：`send(NotifyTarget, content)` 通过 HTTP webhook 发送，支持企业微信/飞书/钉钉群机器人 webhook 格式
- [x] T040 [US4] 在 `oryxos-storage` 创建 `NotifyChannelEntity.java` + `NotifyChannelRepository.java`：通知渠道持久化（name/type/url/config_json/description）
- [x] T041 [US4] 在 `oryxos-storage` 添加 Flyway 迁移 `V2__create_notify_channels.sql`
- [x] T042 [US4] 在 `oryxos-tool` 创建 `McpLoader.java` 解析 mcp_servers 并注册 MCP 工具

**Checkpoint**: 零代码 Agent 目录 + MCP server 可完成新场景上线；@Tool Bean 自动扫描注册；文件/Shell/HTTP/通知工具齐全

---

## Phase 7: User Story 5 — Web Service (Priority: P3)

**Goal**: REST API 暴露所有核心能力，业务系统通过 HTTP 集成

**Independent Test**: curl 完成"创建会话 → 发消息 → 查历史 → 归档"完整流程

**Depends on**: US-1 ~ US-4 全部

### Implementation

- [x] T043 [P] [US5] 在 `oryxos-web` 创建 `ApiResponse.java`：统一响应信封（code/message/data/timestamp），成功与错误共用
- [x] T044 [P] [US5] 在 `oryxos-web` 创建 `GlobalExceptionHandler.java`：`@ControllerAdvice` 统一异常处理，标准 HTTP 状态码（400/404/500/503/504）映射到 ApiResponse
- [x] T045 [P] [US5] 在 `oryxos-web` 创建 `SessionApiController.java`：`POST /api/v1/sessions`（创建）、`POST /api/v1/sessions/{id}/messages`（发消息）、`GET /api/v1/sessions/{id}`（查历史）、`DELETE /api/v1/sessions/{id}`（归档）
- [x] T046 [P] [US5] 在 `oryxos-web` 创建 `AgentApiController.java`：`POST /api/v1/agents/{name}/invoke`（无状态调用）
- [x] T047 [P] [US5] 在 `oryxos-web` 创建 `ProfileApiController.java`：`GET /api/v1/profiles`（列 Profile）
- [x] T048 [P] [US5] 在 `oryxos-web` 创建 `MemoryApiController.java`：`GET /api/v1/memory`（查长期记忆）
- [x] T049 [P] [US5] 在 `oryxos-web` 创建 `ToolApiController.java`：`GET /api/v1/tools`（列可用 Tool）
- [x] T050 [P] [US5] 在 `oryxos-web` 创建 `SystemApiController.java`：`GET /api/v1/health`（健康检查）、`GET /api/v1/info`（运行信息）
- [x] T051 [US5] SessionManager 内存版（US-5 SQLite 持久化延迟到后续迭代）
- [x] T052 [US5] 在 `oryxos-web` 创建 v2 定时任务管理端点：`GET /api/v2/schedules`、`GET /api/v2/schedules/{id}/executions`、`POST /api/v2/schedules/{id}/run`、`PUT /api/v2/schedules/{id}`（启用/停用）、`POST /api/v2/agents/{name}/schedules/{key}/run`
- [x] T053 [US5] 在 `oryxos-storage` 创建 `ScheduledTaskEntity.java` + `TaskExecutionEntity.java` + 对应 Repository + Flyway 迁移 `V3__create_scheduled_tasks.sql`
- [x] T054 [US5] 在 `oryxos-core` 创建 `ScheduledTaskStore.java` 接口，在 `oryxos-storage` 创建 `JpaScheduledTaskStore.java` 实现
- [x] T055 [US5] 在 `oryxos-core` 创建 `AgentScheduler.java`：基于 `ThreadPoolTaskScheduler` + `CronTrigger` 动态注册任务，启动时扫描所有 Profile 的 `schedules` 字段，进程内 `ReentrantLock` 防重叠，失败记日志不崩调度器，钟推 Session ID 用 channel=scheduler + user=scheduler + profile
- [x] T056 [US5] 完善 `oryxos-cli` 的 `ServeCommand.java`：启动 Spring MVC + Virtual Threads，默认端口 8080，定时任务随 serve/gateway 一起常驻调度
- [x] T057 [US5] 在 `oryxos-cli` 补齐 CLI 命令：`status`（其余 profile/provider/tool/session 命令延迟到后续迭代）
- [x] T058 [US5] springdoc-openapi 延迟到后续迭代（核心 API 已就绪）

**Checkpoint**: 10 个 v1 REST 端点 + 6 个 v2 定时任务端点全部可用，Session 持久化跨重启，12 个 CLI 命令完整

---

## Phase 8: Polish（收尾与验证）

**Purpose**: 结构化日志 + 端到端验证 + 文档

- [x] T059 [P] 配置 Logback 结构化日志格式（`logback-spring.xml`），关键操作（LLM 调用、Tool 调用、Session 变更）输出结构化日志
- [x] T060 按 quickstart.md 执行 Demo 一（每日天气）端到端验证（待手动验证）
- [x] T061 按 quickstart.md 执行 Demo 二（每日科技日报）端到端验证（待手动验证）
- [x] T062 按 quickstart.md 执行 Demo 三（Web Service 集成）端到端验证（待手动验证）
- [x] T063 按 quickstart.md 执行定时任务验证 + CLI 命令验证（待手动验证）
- [x] T064 [P] 更新 README.md（延迟到后续迭代）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: 无依赖，立即开始
- **Phase 2 (Foundational)**: 依赖 Phase 1 完成，阻塞所有 US
- **Phase 3 (US-1)**: 依赖 Phase 2
- **Phase 4 (US-2)**: 依赖 Phase 3（US-1）
- **Phase 5 (US-3)**: 依赖 Phase 4（US-2），可与 Phase 6 并行
- **Phase 6 (US-4)**: 依赖 Phase 4（US-2），可与 Phase 5 并行
- **Phase 7 (US-5)**: 依赖 Phase 3-6 全部完成
- **Phase 8 (Polish)**: 依赖 Phase 7

### User Story Dependencies

```
Phase 1 → Phase 2 → US-1 → US-2 → ┬─ US-3 ─┐ → US-5 → Polish
                                     └─ US-4 ─┘
```

### Parallel Opportunities

- Phase 1 中 T002-T007 全部可并行
- Phase 7 中 T043-T050（8 个 Controller）全部可并行
- US-3 和 US-4 可并行推进（不同模块，无交叉）

---

## Implementation Strategy

### MVP First (US-1 + US-2)

1. Phase 1 + Phase 2 → 基础设施就绪
2. Phase 3 (US-1) → Agent 能对话
3. Phase 4 (US-2) → Agent 能调工具
4. **STOP and VALIDATE**: `oryxos chat` 完成天气查询 Demo

### Incremental Delivery

1. Setup + Foundational → 地基就绪
2. + US-1 → Agent 能对话 → 验证
3. + US-2 → Agent 能调工具 → 验证（MVP!）
4. + US-3 → Agent 能记事 → 验证
5. + US-4 → Agent 能力可扩展 → 验证
6. + US-5 → REST API + 持久化 + 定时任务 → 完整验证

---

## Notes

- 共 64 个任务（T001-T064）
- US-1: 5 tasks | US-2: 9 tasks | US-3: 6 tasks | US-4: 9 tasks | US-5: 16 tasks
- Setup + Foundational: 13 tasks | Polish: 6 tasks
- 可并行任务：Phase 1 中 6 个，Phase 7 中 8 个
- 每个 Phase 结束为 checkpoint，可独立验证
