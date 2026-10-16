# Feature Specification: Provider 模块——LLM 协议翻译层

**Feature Branch**: `001-day1-provider-refactor`

**Created**: 2026-10-13

**Status**: Draft

**Input**: User description: "第1天需求：Provider 模块——LLM 协议翻译层。把 OryxOS 内部的统一调用请求翻译成各家 LLM 厂商的协议格式，把响应翻译回统一格式。"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - ReAct 循环调用 LLM 获取响应 (Priority: P1)

ReAct 循环在推理过程中需要 LLM 响应时，调用统一的 Provider 接口，传入当前 Profile 和 Prompt（含消息历史 + 可用工具列表），获得 LLM 的文本响应和/或原始 tool_calls。Provider 层按 Profile 配置的 provider name 找到对应的 LLM 连接实例完成调用，将各家协议差异屏蔽在内部。

**Why this priority**: 这是 OryxOS Agent 核心引擎的基本能力——没有 LLM 调用就没有 Agent 推理。所有其他功能（Tool 执行、Memory、定时任务）都依赖此能力先工作。

**Independent Test**: 可通过单元测试 mock ChatModel 验证 chat() 方法正确返回 ProviderResponse（含 content 和原始 tool_calls），并验证 tool_calls 未被执行。

**Acceptance Scenarios**:

1. **Given** Profile 配置了有效的 provider name 且该 provider 已注册, **When** ReAct 循环调用 chat() 传入正常 prompt 且无工具, **Then** 返回 ProviderResponse，content 非空，toolCalls 为空列表，审计记录 status=SUCCESS
2. **Given** Profile 配置了有效的 provider name, **When** ReAct 循环调用 chat() 传入含工具的 prompt, **Then** 返回 ProviderResponse，toolCalls 包含原始 tool_call 数据（id/name/arguments），这些 tool_calls 未被执行
3. **Given** Profile 配置了一个不存在的 provider name, **When** 调用 chat(), **Then** 抛出 ProviderNotFoundException，无审计记录写入

### User Story 2 - 多 Provider 显式注册与配置分层 (Priority: P2)

运维在 `application.yaml` 中声明多个 LLM Provider 连接（name + type + baseUrl + apiKey），系统启动时显式注册到 Provider 注册表。每个 Agent 的 Profile 通过 provider name 引用其中一个，并指定具体 model 和 temperature。系统不靠类型扫描自动发现，而是显式映射、找不到就报错。

**Why this priority**: 多 Provider 并存是企业场景的基本需求。显式映射让配置可审计、可追踪，避免多 Provider 歧义。

**Independent Test**: 可独立验证 Provider 注册表启动时正确加载配置，按 name 查找返回正确实例，查找不存在的 name 抛出异常。

**Acceptance Scenarios**:

1. **Given** application.yaml 配置了 2 个 provider（qwen-plus 和 gpt-4o）, **When** 系统启动, **Then** Provider 注册表包含 2 条记录，按 name 可正确查找到各自定义
2. **Given** 两个 provider 配置了不同的 baseUrl 和 apiKey, **When** 按 name 查找, **Then** 返回各自的连接实例，不会混淆
3. **Given** 某个 provider 的 apiKey 在运行期间发生变化, **When** 下次调用该 provider, **Then** 使用新 apiKey 重建连接实例（指纹缓存检测到变化）

### User Story 3 - LLM 调用审计全记录 (Priority: P2)

每次 LLM 调用（无论成功或失败）都写入审计表，记录 session 标识、Agent 名称、provider 名称、模型、状态、token 用量、耗时、tool_call 数量、错误信息。这为成本透明度和问题排查提供 Day One 数据基础。

**Why this priority**: 可审计是 OryxOS 的差异化卖点，审计数据必须 Day One 就写入，不能事后补。

**Independent Test**: 可通过集成测试验证成功和失败场景都正确写入审计记录，字段完整。

**Acceptance Scenarios**:

1. **Given** 一次成功的 LLM 调用, **When** 调用完成, **Then** 审计表写入一条记录，status=SUCCESS，包含 token 用量、duration_ms、tool_call_count
2. **Given** 一次失败的 LLM 调用（LLM API 抛异常）, **When** 调用失败, **Then** 审计表写入一条记录，status=FAILED，error_message 非空，duration_ms 已记录

### User Story 4 - Tool Schema 转换（OryxTool → LLM Function Calling 格式）(Priority: P3)

Agent 注册的工具（OryxTool）需要被翻译成各家 LLM 能理解的 Function Calling 格式。Provider 模块负责生成 JSON Schema 供 LLM 识别，并在 LLM 返回 tool_calls 时将原始数据提取出来交给 ReAct 循环处理。Provider 层只生成 schema，不执行工具。

**Why this priority**: Tool 能力是 Agent 的核心扩展手段，但 schema 转换是 Provider 的辅助职责，核心在于 LLM 调用本身。

**Independent Test**: 可独立验证 OryxTool 正确转换为 FunctionCallback（name/description/inputSchema 一致），以及从 ChatResponse 正确提取 tool_calls。

**Acceptance Scenarios**:

1. **Given** 一个 OryxTool 实例（含 name、description、inputSchema）, **When** 转换为 FunctionCallback, **Then** 生成的 schema 包含正确的 name、description、inputSchema
2. **Given** LLM 响应包含多个 tool_calls, **When** 提取 tool_calls, **Then** 返回 ToolCall 列表，每个包含 id、name、arguments（JSON 字符串），数据为原始未处理状态

### Edge Cases

- 当 LLM API 超时或网络不可达时，系统如何处理？→ 捕获异常，写审计（status=FAILED），抛出 ProviderCallException，不静默重试
- 当 Profile 引用了未定义的 provider name 时？→ 立即抛出 ProviderNotFoundException，不静默 fallback
- 当 provider 的 apiKey 为空或无效时？→ 在调用时由底层 ChatModel 抛出认证异常，被捕获后写审计
- 当 Prompt 中包含工具但 LLM 不支持 Function Calling 时？→ 核心阶段不做能力过滤（ProviderDef 已预留 capabilities 字段），由 LLM 自行忽略或报错

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 提供统一的 Provider 接口，接受 session 标识、Profile 和 Prompt（含消息列表 + 工具列表），返回包含文本内容和 tool_calls 的响应
- **FR-002**: 系统 MUST 维护 Provider 的显式注册表，按 name 注册和查找 Provider 定义（name + type + apiKey + baseUrl + fallbacks），找不到时抛出异常而非静默降级
- **FR-003**: 系统 MUST 按 Provider type 构建对应的 LLM 连接实例（支持 openai、azure-openai、qwen、openai-compatible 四种类型）
- **FR-004**: 系统 MUST 实现 LLM 连接实例的指纹缓存（fingerprint = apiKey + "|" + baseUrl），配置未变时复用实例，配置变化时自动重建
- **FR-005**: 系统 MUST 按 Provider type 构建对应类型的 ChatOptions（合并 Profile 层的 model 和 temperature 配置）
- **FR-006**: 系统 MUST 将内部工具抽象（OryxTool）转换为 LLM 可理解的 Function Calling schema 格式，只生成 schema 不执行
- **FR-007**: 系统 MUST 从 LLM 原始响应中提取 tool_calls（id + name + arguments），原样返回给调用方
- **FR-008**: 系统 MUST 在每次 LLM 调用（成功和失败）后写入审计记录，包含 session 标识、Agent 名称、provider 名称、模型、状态、token 用量、耗时、tool_call 数量、错误信息
- **FR-009**: 系统 MUST 在启动时从全局配置（application.yaml）加载 Provider 定义到注册表
- **FR-010**: 配置 MUST 分层——全局层管"有什么"（连接资源池：name + type + baseUrl + apiKey），Profile 层管"怎么用"（provider name + model + temperature）
- **FR-011**: 所有凭证（apiKey）MUST 通过环境变量注入，不得明文硬编码

### Key Entities

- **ProviderDef**: Provider 定义——name（唯一标识）、type（openai/azure-openai/qwen/openai-compatible）、apiKey、baseUrl、fallbacks（预留）
- **ProviderResponse**: LLM 响应——content（文本内容）、toolCalls（原始 tool_call 列表）、usage（token 用量）
- **ToolCall**: Tool 调用——id、name、arguments（JSON 字符串）
- **Usage**: Token 用量——promptTokens、completionTokens、totalTokens
- **LlmCallRecord**: 审计记录——session_id、agent_name、provider_name、model、status、token 用量、duration_ms、tool_call_count、error_message、timestamp

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Agent 推理循环可通过统一接口调用任意已配置的 LLM Provider 并获得正确响应
- **SC-002**: 多 Provider 并存时，按 Profile 配置的 provider name 100% 准确地路由到对应 LLM 实例，无误路由
- **SC-003**: 100% 的 LLM 调用（成功和失败）在发生时刻写入审计记录，无遗漏
- **SC-004**: 配置变更后（apiKey/baseUrl 变化），系统在下一次调用时自动使用新配置，无需重启
- **SC-005**: 代码中不存在 Spring AI ChatClient 调用、Advisor 使用或自动 tool 执行路径
- **SC-006**: 所有单元测试和集成冒烟测试通过，构建门禁全绿

## Assumptions

- Spring AI 1.0.0-M6 BOM 已锁定，各 ChatModel 实现类（OpenAiChatModel 等）的 API 以该版本为准
- SQLite 数据库和 Flyway 迁移已就位，llm_calls 表结构已在 V1 迁移脚本中定义
- 核心阶段不做 E2E 测试（需要真实 LLM API key），只通过 mock 验证逻辑正确性
- Provider 故障时直接报错，不做 fallback/hedge racing/circuit breaker（已预留 fallbacks 字段）
- Profile 的 provider name 必须精确匹配全局配置中定义的 name，不做模糊匹配或隐式降级
- ChatOptions 类型因 Provider 而异，需要按 type switch 构建对应类型（Spring AI 各家实现不共享统一 ChatOptions 接口）
