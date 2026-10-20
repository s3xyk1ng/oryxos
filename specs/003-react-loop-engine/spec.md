# Feature Specification: ReAct 模块——Agent 引擎（Reason + Act 循环）

**Feature Branch**: `002-day2-react-refactor`

**Created**: 2026-10-16

**Status**: Draft

**Input**: User description: "第2天需求：ReAct 模块——Agent 引擎。把用户消息变成 Agent 最终响应的核心循环引擎。"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - ReAct 循环驱动 Agent 推理 (Priority: P1)

用户通过 CLI/REST/Scheduler 发消息，AgentService 统一入口接收，设置 ProfileContext（ThreadLocal），调 ReActLoop 跑完循环，持久化 Session，finally 清理 ProfileContext。ReActLoop 内部 for 循环驱动：组装 Prompt → 调 LLM → 判断是否有工具调用 → 执行工具 → 回填结果 → 继续循环。严格 maxIterations（默认 10）限制，达到上限强制终止。

**Why this priority**: 这是 OryxOS 最核心的代码——Agent 的大脑。没有 ReAct 循环就没有 Agent 推理，所有其他功能（Tool 执行、Memory、定时任务）都依赖此循环先工作。

**Independent Test**: 可通过单元测试 mock ProviderService 和 ToolExecutor，验证循环正确执行：无 tool_call 时直接返回、有 tool_call 时执行工具并继续、达到 maxIterations 时强制终止。

**Acceptance Scenarios**:

1. **Given** Profile 配置了有效 provider 且 LLM 返回无 tool_call 的响应, **When** ReActLoop.run() 执行, **Then** 返回 ProviderResponse，Session 中有 user + assistant 消息，循环 1 次结束
2. **Given** LLM 返回含 tool_calls 的响应, **When** ReActLoop.run() 执行, **Then** ToolExecutor 执行工具，结果追加到 Session（role=tool），进入下一轮循环
3. **Given** LLM 每次都返回 tool_calls, **When** 循环达到 maxIterations（默认 10）, **Then** 强制返回最后一次响应，不抛异常、不无限循环
4. **Given** 用户消息传入, **When** ReActLoop.run() 执行, **Then** Session.messages 第一条是 role=user，每轮追加 assistant 消息

### User Story 2 - Prompt 四段拼接与上下文管理 (Priority: P1)

PromptBuilder 按四部分顺序拼接 Prompt：① system prompt（AGENT.md + Bootstrap + Skills + 当前日期时间）② Memory 注入 ③ 对话历史（按 maxHistoryTurns 默认 20 轮截断）④ Tool 列表（从 ToolRegistry 获取，Function Calling 格式）。自己从 ToolRegistry 取 tools，不依赖外部传入。

**Why this priority**: Prompt 质量直接决定 LLM 输出质量。时间戳对定时场景至关重要（LLM 不知道今天几号），历史截断防止 token 撑爆。

**Independent Test**: 可通过单元测试 mock ContextLoader/MemoryPort/ToolRegistry，验证四段拼接正确、截断生效、时间戳存在。

**Acceptance Scenarios**:

1. **Given** 有效的 Profile 和 Session, **When** PromptBuilder.build() 执行, **Then** 返回的 Prompt 包含 bootstrap + agentBody + skills + 当前时间（RFC_1123 格式）
2. **Given** Session 有 50 条消息且 maxHistoryTurns=20, **When** PromptBuilder.build() 执行, **Then** Prompt 中只包含最近 40 条消息
3. **Given** MemoryPort 返回有效内容, **When** PromptBuilder.build() 执行, **Then** system prompt 中包含 memory 内容
4. **Given** MemoryPort 为 null 或返回空, **When** PromptBuilder.build() 执行, **Then** 不报错，system prompt 不含 memory 段

### User Story 3 - 工具执行与全量审计 (Priority: P1)

ToolExecutor 从 ToolRegistry 查找工具 → Sandbox 检查 → 执行 → 审计。无论成功/失败/找不到/沙箱拒绝，都在 finally 块中写一条 tool_invocations 记录。审计包含 toolCallId 和 duration。

**Why this priority**: 工具执行是 Agent 的"手"，审计是合规底线。四种情况全覆盖确保审计链完整。

**Independent Test**: 可通过单元测试 mock ToolRegistry/Sandbox/AuditPort，验证四种场景的返回值和审计记录。

**Acceptance Scenarios**:

1. **Given** ToolRegistry 中存在指定工具, **When** ToolExecutor.execute() 执行, **Then** 返回 ToolResult(success=true)，审计记录 success=true, errorMessage=null
2. **Given** ToolRegistry 中不存在指定工具, **When** ToolExecutor.execute() 执行, **Then** 返回 ToolResult(success=false, retryable=false)，审计记录 success=false, errorMessage="Tool not found: xxx"
3. **Given** Sandbox 拒绝工具调用, **When** ToolExecutor.execute() 执行, **Then** 返回 ToolResult(success=false)，审计记录 success=false
4. **Given** 工具执行抛异常, **When** ToolExecutor.execute() 执行, **Then** 返回 ToolResult(success=false, retryable=true)，审计记录 success=false

### User Story 4 - 入口编排与 ProfileContext 生命周期 (Priority: P2)

AgentService.process() 是三种触发源共用的统一入口。设置 ProfileContext（ThreadLocal）→ 调 ReActLoop.run() → 持久化 Session → finally 清理 ProfileContext。异常时 ProfileContext 必须清理，防止虚拟线程复用时 Profile 泄漏。

**Why this priority**: 入口编排保证每次请求的上下文隔离和清理。ProfileContext 泄漏在单请求测试里不报错，只在并发复用时串号。

**Independent Test**: 可通过单元测试验证正常流程和异常流程的 ProfileContext 清理、Session 持久化。

**Acceptance Scenarios**:

1. **Given** 有效的 Session 和 message, **When** AgentService.process() 执行, **Then** 返回 LLM 响应内容，ProfileContext.get() == null，sessionManager.save() 被调用
2. **Given** ReActLoop.run() 抛异常, **When** AgentService.process() 执行, **Then** 异常上抛，ProfileContext.get() == null（finally 清理）
3. **Given** Profile 不存在, **When** AgentService.process() 执行, **Then** 抛出 IllegalArgumentException

### User Story 5 - 上下文文件加载与错误约束 (Priority: P2)

ContextLoader 加载 AGENT.md / Bootstrap / Skills 文件。AGENT.md 缺失抛 AgentConfigException（fail-fast），Bootstrap 文件缺失 log.warn（防止人格丢失），每次现读不缓存。

**Why this priority**: AGENT.md 是 Agent 的核心指令，缺失则无法工作；Bootstrap 是人格文件，缺失应告警但不中断。

**Independent Test**: 可通过单元测试验证文件存在/缺失时的行为。

**Acceptance Scenarios**:

1. **Given** AGENT.md 文件存在, **When** ContextLoader.loadAgentBody() 执行, **Then** 返回去掉 frontmatter 后的正文
2. **Given** AGENT.md 文件不存在, **When** ContextLoader.loadAgentBody() 执行, **Then** 抛出 AgentConfigException
3. **Given** Bootstrap 文件部分缺失, **When** ContextLoader.loadBootstrap() 执行, **Then** 缺失文件 log.warn，其余正常返回
4. **Given** 文件内容被修改, **When** 连续两次调用 ContextLoader, **Then** 第二次返回新内容（无缓存）

## Functional Requirements *(mandatory)*

- **FR1**: ReActLoop.run(Session, Profile, String message) 返回 ProviderResponse，内部完成完整 ReAct 循环（for 循环 + maxIterations + 消息累积 + 终止判断）
- **FR2**: AgentService.process(Session, String message) 返回 String，统一入口：ProfileContext.set → reActLoop.run → sessionManager.save → finally ProfileContext.clear
- **FR3**: PromptBuilder.build(Profile, Session) 返回 Prompt，四段拼接（system prompt + memory + 截断历史 + tool schema），自己从 ToolRegistry 取 tools
- **FR4**: ToolExecutor.execute(Session, ToolCall) 返回 ToolResult，从 ToolRegistry 查找工具，Sandbox 检查，执行，审计（finally 全覆盖）
- **FR5**: ContextLoader 加载 AGENT.md/Bootstrap/Skills，AGENT.md 缺失抛 AgentConfigException，Bootstrap 缺失 log.warn，不缓存
- **FR6**: ProfileContext（ThreadLocal），set/get/clear 三个静态方法
- **FR7**: Message 模型新增 toolCallId 字段
- **FR8**: ToolInvocationEntity + Repository 新增 toolCallId 字段，V5 迁移脚本
- **FR9**: 删除旧代码：LlmPort, LlmPortFactory, ProviderLlmPortFactory, ToolCallParser, ToolCallRequest

## Success Criteria *(mandatory)*

### Measurable Outcomes

1. ReAct 循环在 LLM 无 tool_call 时 1 轮终止，返回有效响应
2. ReAct 循环在 LLM 持续返回 tool_call 时，达到 maxIterations（默认 10）强制终止
3. Prompt 末尾包含当前日期时间（RFC_1123 格式）
4. 对话历史超过 maxHistoryTurns（默认 20 轮）时自动截断
5. 工具调用无论成功/失败/找不到/沙箱拒绝，都写入 tool_invocations 审计记录
6. 请求结束后 ProfileContext.get() == null（无 ThreadLocal 泄漏）
7. AGENT.md 缺失时抛出 AgentConfigException，不静默降级
8. 全部 48 个单元测试验收点通过
9. mvn clean verify 全绿

## Scope & Boundaries

### In Scope

- ReAct 循环引擎（ReActLoop, AgentService, PromptBuilder, ToolExecutor, ContextLoader）
- ProfileContext ThreadLocal 管理
- 数据模型变更（Message.toolCallId, ToolInvocationEntity.toolCallId）
- 旧代码清理（LlmPort/LlmPortFactory/ToolCallParser/ToolCallRequest）

### Out of Scope

- 工具并行调用（tool_calls 按顺序执行）
- Agent 之间相互委托
- 流式输出（扩展阶段用 SSE）
- 上下文压缩（核心阶段只截断不总结）
- Spring AI ChatClient / Advisor / Agent 抽象
- ReActLoop 内的 LLM 审计（ProviderService 内置）

## Dependencies & Assumptions

### Dependencies

- Day 1 Provider 模块已交付（ProviderService, ProviderResponse, ToolCall, Prompt, AuditLogger）
- Spring AI 1.0.0-M6 BOM（已锁定）
- SQLite + Flyway（tool_invocations 表已存在 V1 建表脚本）

### Assumptions

- 凭证走环境变量占位，不落明文
- ToolRegistry 已存在或将在后续天交付
- Sandbox 接口可选注入（@Autowired(required=false)）
- MemoryPort 接口可选注入（@Autowired(required=false)）
