# 功能规格：OryxOS 核心运行时内核

**Feature Branch**: `001-oryxos-core`

**创建日期**: 2026-10-09

**状态**: Draft

**输入**: 用户描述: "基于 docs/DemandAnalysis.md 实现 OryxOS 核心阶段的 5 大核心能力——对接 LLM、ReAct 循环、Memory 记忆、Tool 体系、Web Service"

---

## Clarifications

### Session 2026-10-09

- Q: OryxOS 核心阶段连接 MCP server 时，应支持哪种传输协议？ → A: 核心阶段同时支持 stdio + SSE 两种传输协议，覆盖面最广。
- Q: 在 CLI 交互模式下，用户身份标识如何确定？ → A: CLI 启动时交互式提示用户输入用户名，用于 Session ID 的联合标识生成。
- Q: 定时任务（AgentScheduler）在核心阶段是否需要持久化执行历史和管理状态？ → A: 核心阶段做完整实现：任务状态持久化到 SQLite、执行历史可查、提供启用/停用和立即执行的管理端点。

---

## 用户场景与测试 *(必填)*

OryxOS 核心阶段交付 Agent OS 的运行时内核。以下 5 个 User Story 按依赖关系排列，每个都是可独立测试的功能切片。

推进顺序：US-1 → US-2 → (US-3 ‖ US-4) → US-5

---

### User Story 1 - 对接 LLM（Provider 抽象）(Priority: P1)

某企业的 AI 团队在 OryxOS 上配置一个 Agent，选择 DeepSeek 作为 LLM Provider。Agent 能够理解用户输入并生成自然语言回复，且不绑定任何特定 LLM 厂商——将来切换到 Kimi 或通义时，只需修改配置，Agent 逻辑无需变动。

**为什么是这个优先级**：LLM 调用是所有 Agent 能力的基础。没有 LLM 调用，ReAct 循环、记忆、工具都无从谈起。US-1 是整个系统的基石。

**独立测试**：可以通过配置一个 Provider、向 Agent 发送一条消息并收到 LLM 回复来独立验证。此场景验证了 Provider 抽象和 LLM 连通性，交付了"Agent 能对话"的基本价值。

**验收场景**：

1. **Given** 已配置 DeepSeek Provider 并创建了一个 Agent，**When** 通过 CLI 向 Agent 发送"你好"，**Then** Agent 通过 DeepSeek 生成回复并返回给用户。
2. **Given** 同时配置了 DeepSeek 和 Kimi 两个 Provider，**When** 创建两个 Agent 分别使用不同 Provider，**Then** 两个 Agent 各自通过对应 Provider 正确响应，互不干扰。
3. **Given** 一个 Agent 配置了某个 Provider，**When** 修改配置切换到另一个 Provider，**Then** Agent 使用新 Provider 响应，无需修改 Agent 定义本身。
4. **Given** Provider 的 API key 通过环境变量注入，**When** OryxOS 启动时，**Then** 正确读取环境变量中的 API key 并建立 Provider 连接。

---

### User Story 2 - ReAct 循环（Agent 大脑）(Priority: P1)

用户向 Agent 发出"查一下北京天气并告诉我穿什么"的请求。Agent 自主决定调用天气查询工具获取数据，根据返回结果生成穿搭建议并回复用户。整个过程无需用户预先编排流程，Agent 在运行时自主决定下一步行动。

**为什么是这个优先级**：ReAct 循环是 Agent 的核心工作机制，与 US-1 同为最高优先级。没有 ReAct 循环，Agent 只能做一问一答，无法调用工具完成多步骤任务。

**独立测试**：可以通过 CLI 与 Agent 对话，让 Agent 调用 HTTP 工具完成一个需要"查询外部数据 + 生成回复"的任务来验证。此场景交付了"Agent 能自主完成多步骤任务"的核心价值。

**验收场景**：

1. **Given** Agent 已配置 Provider 和 HTTP 工具，**When** 用户发送"查一下北京天气并告诉我穿什么"，**Then** Agent 通过 ReAct 循环自主调用 HTTP 工具获取天气数据，根据结果生成穿搭建议返回给用户。
2. **Given** Agent 正在执行 ReAct 循环，**When** LLM 响应不包含工具调用，**Then** 循环结束，最终响应返回给用户。
3. **Given** Agent 的 `max_iterations` 配置为 10，**When** ReAct 循环达到 10 次迭代，**Then** 循环强制终止，返回当前已有的最佳响应。
4. **Given** Agent 完成一次完整对话，**When** 查看 Session 记录，**Then** 可以看到完整的 LLM 调用链和 Tool 调用链，每轮迭代的输入输出都被正确记录。
5. **Given** 用户通过 CLI 启动交互式对话，**When** 连续发送多条消息，**Then** Agent 正确维护对话上下文，后续消息能引用之前的对话内容。

---

### User Story 3 - Memory 三层记忆（让 Agent 记得住）(Priority: P2)

用户在第一次对话中告诉 Agent"我的项目用 Spring Boot，部署在 K8s 上"。Agent 主动将这条信息保存到长期记忆。重启 OryxOS 后开启新会话，用户问"帮我看看我的项目能用什么数据库"，Agent 在回复中引用了之前记住的技术栈偏好。

**为什么是这个优先级**：Memory 是 Agent OS 区别于普通 chatbot 的核心能力。它依赖 US-1 和 US-2（记忆注入 ReAct 循环的 prompt），但不影响 US-4。可与 US-4 并行推进。

**独立测试**：可以通过两次独立会话验证——第一次会话写入记忆，重启后第二次会话检查 Agent 是否引用了之前保存的信息。此场景交付了"Agent 跨对话记住用户偏好"的核心体验。

**验收场景**：

1. **Given** Agent 处于对话中，**When** 用户说"记住：我的项目用 Spring Boot"，**Then** Agent 调用 `save_memory` 工具将信息追加到 `MEMORY.md` 文件。
2. **Given** `MEMORY.md` 中已保存了用户偏好，**When** 重启 OryxOS 并开启新会话，**Then** Agent 启动时自动加载 `MEMORY.md` 内容到 system prompt，在后续回复中体现已记住的偏好。
3. **Given** 用户要求回忆之前保存的信息，**When** Agent 调用 `recall_memory` 工具按关键词检索，**Then** 返回 `MEMORY.md` 中匹配的相关内容。
4. **Given** 当前对话历史很长，**When** 上下文超过配置的 `max_history_turns`（默认 20 轮），**Then** 系统截断早期对话，保留近期对话和 system prompt 完整。
5. **Given** `MEMORY.md` 文件内容超过 4000 字，**When** 系统加载长期记忆，**Then** 对超长内容进行截断处理，保证不影响 LLM 上下文窗口。

---

### User Story 4 - Plugin Tool 体系（让 Agent 能干事）(Priority: P2)

业务方想给 Agent 接入 GitHub 能力。他们不写 Java 代码，只创建一个 Agent 目录（`AGENT.md` 描述任务），配置社区现成的 GitHub MCP server。Agent 启动后能读取任务描述、连接 MCP server、调用 GitHub 工具完成任务。

**为什么是这个优先级**：Tool 体系让 Agent 从"能对话"升级为"能做事"。它依赖 US-2（Tool 被 ReAct 循环调用），但不影响 US-3。可与 US-3 并行推进。

**独立测试**：可以通过创建一个零代码 Agent 目录、配置 MCP server、让 Agent 调用外部工具完成一个具体任务来验证。此场景交付了"业务方无需写代码即可扩展 Agent 能力"的核心价值。

**验收场景**：

1. **Given** 业务方创建了 Agent 目录并写好 `AGENT.md`，**When** OryxOS 启动时，**Then** 系统自动扫描 Agent 目录，解析 frontmatter 生成 Profile，Agent 按配置可用。
2. **Given** Agent 配置了 MCP server，**When** Agent 在 ReAct 循环中决定调用 MCP 工具，**Then** 系统通过 MCP 协议转发请求到对应 MCP server，执行结果正确回传给 Agent 继续推理。
3. **Given** Agent 调用文件操作工具（`read_file`），**When** 请求的路径不在白名单内，**Then** Sandbox 校验拒绝执行，返回权限错误。
4. **Given** Agent 调用 Shell 工具，**When** 命令不在允许列表中，**Then** Sandbox 校验拒绝执行，返回命令不被允许的错误。
5. **Given** Agent 调用 HTTP 工具，**When** 请求的域名不在白名单内，**Then** Sandbox 校验拒绝执行，返回域名不被允许的错误。
6. **Given** 一个 Java 开发者用 `@Tool` 注解写了一个 Spring Bean，**When** OryxOS 启动时，**Then** 该工具被自动扫描注册到 ToolRegistry，Agent 可以正常调用。
7. **Given** Agent 目录绑定了公共 Skill，**When** Agent 运行时，**Then** 已绑定 Skill 的名称和描述注入 system prompt，Skill 正文通过 `read_file` 按需读取，未绑定的 Skill 不可见。

---

### User Story 5 - Web Service（对外接口暴露）(Priority: P3)

某企业的业务系统想集成 OryxOS 的 Agent 能力。开发人员通过 REST API 创建会话、发送消息、查询历史、调用 Agent，全程通过 HTTP 接口完成，无需了解 OryxOS 内部实现。

**为什么是这个优先级**：Web Service 是 OryxOS 区别于个人助手项目的关键能力，是企业把 AI 能力嵌入已有业务系统的唯一通道。它依赖 US-1 到 US-4 全部就绪后才能对外暴露完整能力，因此排在最后实施，但重要性很高。

**独立测试**：可以通过外部 HTTP 客户端（如 curl）调用 REST API 完成一次完整的"创建会话 → 发消息 → 查历史 → 归档"流程来验证。此场景交付了"业务系统通过 HTTP 集成 Agent 能力"的核心价值。

**验收场景**：

1. **Given** OryxOS 以 HTTP 服务模式启动，**When** 外部系统调用 `POST /api/v1/sessions`，**Then** 成功创建会话并返回 session ID。
2. **Given** 已有活跃会话，**When** 外部系统调用 `POST /api/v1/sessions/{id}/messages` 发送消息，**Then** Agent 处理消息并返回响应，完整对话记录可通过 `GET /api/v1/sessions/{id}` 查询。
3. **Given** 已有完成的会话，**When** 外部系统调用 `DELETE /api/v1/sessions/{id}`，**Then** 会话被归档，状态变为 `archived`。
4. **Given** 已配置多个 Agent，**When** 外部系统调用 `POST /api/v1/agents/{name}/invoke`，**Then** 指定 Agent 处理请求并返回无状态响应。
5. **Given** OryxOS 正在运行，**When** 外部系统调用 `GET /api/v1/profiles`、`GET /api/v1/tools`、`GET /api/v1/memory`，**Then** 分别返回可用 Agent 列表、可用工具列表、长期记忆内容。
6. **Given** OryxOS 正在运行，**When** 外部系统调用 `GET /api/v1/health` 和 `GET /api/v1/info`，**Then** 分别返回健康状态和运行信息。
7. **Given** OryxOS 重启，**When** 之前持久化的 Session 数据存在，**Then** Session 可从 SQLite 恢复，正在进行的对话不丢失。
8. **Given** 12 个 CLI 命令全部实现，**When** 用户执行 `oryxos init`、`oryxos chat`、`oryxos serve`、`oryxos profile list/create/show/delete`、`oryxos provider list`、`oryxos tool list`、`oryxos session list`、`oryxos status`、`oryxos gateway`，**Then** 每个命令按预期执行。

---

### 边界情况

- LLM Provider API 返回错误或超时时，系统如何处理？→ 直接报错给 Agent，核心阶段不做 fallback。
- ReAct 循环中 Tool 调用失败时，系统如何处理？→ 将错误信息作为 Tool 结果回传给 LLM，由 LLM 决定下一步（重试、换工具、或放弃）。
- `MEMORY.md` 文件不存在或为空时，系统如何处理？→ 正常启动，长期记忆部分为空，Agent 可通过 `save_memory` 创建内容。
- 多个 Agent 同时运行时的 Session 隔离？→ 按 Channel + 用户 + Agent 联合标识生成唯一 session_id，互不干扰。CLI 场景下启动时交互式提示用户输入用户名。
- 上下文超过 LLM context window 上限时？→ 截断早期对话，保留 system prompt 和最近 N 轮对话。
- MCP server 连接断开或超时时？→ 返回连接错误给 Agent，由 LLM 决定是否重试。
- Sandbox 白名单配置为空时？→ 所有对应类型的操作全部拒绝。
- 外部系统调用 REST API 传入不存在的 Agent 名称时？→ 返回 404 错误。
- 单条消息超过大小限制时？→ 返回 400 错误，提示消息过大。
- Agent 调用工具时参数格式错误？→ 参数校验失败，返回错误信息给 LLM 重新生成参数。

---

## 需求 *(必填)*

### 功能需求

**工作区与 Agent 管理**

- **FR-001**：系统**必须**提供工作区初始化能力，创建 `.oryxos/` 目录及完整子目录结构（agents、skills、memory、sessions、logs），操作幂等。
- **FR-002**：系统**必须**支持通过 Agent 目录（`AGENT.md`）定义 Agent，frontmatter 声明运行配置，正文作为任务指令。
- **FR-003**：系统**必须**支持多个 Agent 在同一实例并存，各自独立配置 Provider、Tool、Channel。

**LLM 对接**

- **FR-004**：系统**必须**通过 Provider 抽象层对接主流 LLM（DeepSeek、通义、Kimi、智谱、混元、豆包等），Agent 不感知具体调的是哪家模型。
- **FR-005**：系统**必须**维护 provider name 到 ChatModel 的显式映射，不依赖类型扫描。
- **FR-006**：系统**必须**支持通过环境变量注入 API key，不明文硬编码。

**ReAct 循环**

- **FR-007**：系统**必须**实现 ReAct 循环（Reason + Act），Agent 自主决定何时调用哪个工具，循环直到无工具调用或达到最大迭代次数。
- **FR-008**：系统**必须**在每轮迭代中将 LLM 响应和 Tool 结果追加到 Session 对话历史。
- **FR-009**：系统**必须**支持 `max_iterations` 配置（默认 10），防止 Agent 陷入死循环。

**Memory 记忆**

- **FR-010**：系统**必须**提供会话记忆（当前对话历史）和长期记忆（`MEMORY.md` 文件）两层记忆能力。
- **FR-011**：系统**必须**提供 `save_memory` 和 `recall_memory` 两个内置工具，让 Agent 主动读写长期记忆。
- **FR-012**：系统**必须**在 Agent 启动时将 `MEMORY.md` 内容注入 system prompt。
- **FR-013**：系统**必须**通过统一的 Memory 门面对 ReAct 循环暴露记忆读写接口。

**Tool 体系**

- **FR-014**：系统**必须**提供内置工具集：文件操作（`read_file`、`write_file`、`list_dir`）、Shell 执行（`shell`）、HTTP 请求（`http_get`、`http_post`）、通知推送（`notify`）。
- **FR-015**：系统**必须**支持零代码 Plugin Tool 方式：Agent 目录 + 复用社区 MCP server。
- **FR-016**：系统**必须**支持轻代码 Plugin Tool 方式：业务方用任何语言写 MCP server，系统通过 MCP 协议连接。核心阶段同时支持 stdio 和 SSE 两种 MCP 传输协议。
- **FR-017**：系统**必须**支持重代码 Plugin Tool 方式：`@Tool` 注解的 Java Spring Bean 自动扫描注册。
- **FR-018**：系统**必须**将所有 Tool 统一包装为 `OryxTool` 接口，ReAct 循环不感知 Tool 来源。
- **FR-019**：系统**必须**对所有 Tool 执行前进行 Sandbox 校验（文件路径白名单、Shell 命令白名单、HTTP 域名白名单）。

**Web Service**

- **FR-020**：系统**必须**通过 REST API 暴露所有核心能力，包含会话管理（4 个端点）、Agent 调用（1 个端点）、Profile/Memory/Tool 查询（3 个端点）、系统状态（2 个端点），共 10 个核心端点。
- **FR-021**：系统**必须**提供统一的错误处理机制，标准 HTTP 状态码 + 统一响应信封。
- **FR-022**：系统**必须**支持通过 `oryxos serve` 命令启动 HTTP API 服务。

**Session 管理**

- **FR-023**：系统**必须**按 Channel + 用户 + Agent 联合标识管理 Session，支持多会话并存。
- **FR-024**：系统**必须**将 Session 数据持久化到 SQLite，支持跨重启恢复。
- **FR-025**：系统**必须**在上下文超过配置限制时截断早期对话，保留近期对话。

**审计**

- **FR-026**：系统**必须**在每次 Tool 调用时写入 `tool_invocations` 表（工具名称、输入、结果、是否成功、耗时）。
- **FR-027**：系统**必须**在每次 LLM 调用时写入 `llm_calls` 表（Provider、模型、token 用量、耗时）。

**CLI 命令行**

- **FR-028**：系统**必须**提供 12 个 CLI 命令：`init`、`status`、`chat`、`serve`、`gateway`、`profile list/create/show/delete`、`provider list`、`tool list`、`session list`。
- **FR-029**：系统**必须**支持三种运行模式：交互对话（`chat`）、HTTP API（`serve`）、守护进程（`gateway`），共享同一份配置和 Session 存储。

**定时任务**

- **FR-030**：系统**必须**支持 Agent 的 `schedules` 配置，按 cron 表达式定时自动触发 Agent，与 CLI/Web Service 共用同一条处理链路。定时任务状态持久化到 SQLite（任务登记信息、运行状态、执行历史），提供启用/停用、立即执行、执行历史查询的管理端点。

---

### 关键实体

- **Agent（智能体）**：一个具象的业务智能体，定义本体是一个目录（`AGENT.md` + 可选 Skill 绑定、脚本、参考文档）。Agent 是配置出来的，不是写代码写出来的。
- **Profile（配置）**：运行时宿主配置对象，决定 Agent "怎么跑"：绑定的 LLM Provider、可用 Tool 列表、Channel、定时规则。由 `AGENT.md` frontmatter 派生。
- **Provider（供应商）**：LLM API 服务的抽象，实现统一接口让 Agent 不感知具体调的是哪家模型。
- **Session（会话）**：用户和 Agent 一次对话的上下文容器，包含对话历史、当前上下文、临时变量。
- **Tool（工具）**：Agent 可以调用的外部能力，分内置 Tool 和 Plugin Tool。统一通过 `OryxTool` 接口抽象。
- **Memory（记忆）**：Agent 跨对话保留的状态，核心阶段实现会话记忆和长期记忆（`MEMORY.md`）两层。
- **Sandbox（沙箱）**：工具执行的隔离环境，核心阶段为应用层白名单校验。

---

## 成功标准 *(必填)*

### 可度量结果

- **SC-001**：用户可以在 3 分钟内完成工作区初始化、创建第一个 Agent、并通过 CLI 与 Agent 完成首次对话。
- **SC-002**：Agent 通过 ReAct 循环完成一个需要调用外部工具的多步骤任务（如查天气 + 生成建议），全程无需人工干预执行路径。
- **SC-003**：Agent 在第一次对话中保存的用户偏好，在重启后的第二次对话中被正确引用，跨会话记忆保持率 100%。
- **SC-004**：业务方可以在不写任何 Java 代码的情况下，通过创建 Agent 目录 + 配置 MCP server 完成一个新 Agent 的上线。
- **SC-005**：外部系统通过 REST API 完成"创建会话 → 发消息 → 查历史 → 归档"完整流程，端到端延迟（不含 LLM 调用）不超过 200ms。
- **SC-006**：单节点支持 10 个 Agent 稳定运行，100 个并发 Session 不出现数据串扰或丢失。
- **SC-007**：所有 Tool 调用和 LLM 调用 100% 落入审计表，可追溯每一次调用的输入、输出和耗时。
- **SC-008**：定时任务到点自动触发 Agent 执行，与手动触发的结果一致，验证"钟推"和"人推"复用同一条链路。任务状态和执行历史重启不丢失，可通过管理端点查看、启用/停用、立即执行。

---

## 假设

- 核心阶段假设部署在企业内网，REST API 无需认证机制（认证放扩展阶段）。
- 核心阶段只内置 CLI 一种 Channel，企业微信/飞书/钉钉等 IM Channel 放扩展阶段。
- 核心阶段 LLM Provider 故障时直接报错，不做 fallback 和自动切换（放扩展阶段）。
- 核心阶段 Tool 调用为顺序执行（一次响应中多个 Tool 调用不并行）。
- 核心阶段不做流式响应（SSE），所有响应为同步返回。
- 长期记忆核心阶段使用 `MEMORY.md` 文件 + 关键词检索，不引入向量数据库。
- 核心阶段 Sandbox 为应用层白名单校验（"劝阻级"防线），不做容器级隔离。
- 核心阶段不做 Agent 间任务委托和跨 Agent 协作。
- 用户具备基本的 Java/Spring Boot 开发环境，能编译和运行 Maven 项目。
- LLM Provider 的 API key 由用户自行获取和配置，OryxOS 不提供 API key。
