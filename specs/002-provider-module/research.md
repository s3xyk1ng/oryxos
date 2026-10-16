# Research: Provider 模块

**Feature**: 002-provider-module | **Date**: 2026-10-13

## R1: Spring AI M6 ChatModel API 核实

**Decision**: 使用 `ChatModel.call(Prompt)` 直接调用，返回 `ChatResponse`

**Rationale**: 已核实 spring-ai-core 1.0.0-M6 jar 中存在以下类：
- `org.springframework.ai.chat.model.ChatModel` — 核心调用接口
- `org.springframework.ai.chat.model.ChatResponse` — 响应封装
- `org.springframework.ai.chat.prompt.ChatOptions` — 选项接口
- `org.springframework.ai.chat.messages.AssistantMessage.ToolCall` — tool_call 内部类
- `org.springframework.ai.model.function.FunctionCallback` — 工具 schema 接口
- `org.springframework.ai.openai.OpenAiChatModel` / `OpenAiChatOptions` — OpenAI 实现

**Alternatives considered**:
- `ChatClient` — 禁止使用（会自动执行 tool，违反宪法原则 II）
- 直接 HTTP 调用 LLM API — 重复造轮子，Spring AI 已封装协议差异

## R2: Azure OpenAI 依赖状态

**Decision**: Day 1 在 `buildChatModel()` switch 中为 `azure-openai` 预留分支，但标注 TODO 并注明需新增 `spring-ai-azure-openai` 依赖。核心阶段只确保 `openai`/`qwen`/`openai-compatible` 三种 type 可工作。

**Rationale**: 当前 oryxos-provider POM 未引入 `spring-ai-azure-openai`，强行编译 azure 分支会导致 ClassNotFoundException。课件定义了四种 type，但 azure 可在需要时增量添加。

**Alternatives considered**:
- 立即引入 azure 依赖 — 增加不必要的依赖体积，核心阶段无 azure 使用场景
- 不预留 azure 分支 — 与课件定义不一致，后续补做时容易遗漏

## R3: FunctionCallback 构建方式

**Decision**: 使用 `FunctionCallback.builder().function(name, function).description(desc).inputSchema(schema).build()` 模式构建。function 参数为 throw UnsupportedOperationException 的占位 lambda（Provider 层不执行 tool）。

**Rationale**: FunctionCallback 在 M6 中位于 `org.springframework.ai.model.function` 包，builder 模式是标准构建方式。占位 function 确保即使被意外调用也会立即报错而非静默执行。

**Alternatives considered**:
- 实现自定义 FunctionCallback — 过度工程，Spring AI 已提供 builder
- 不传 function（null）— 可能导致 NPE

## R4: SQLite ID 策略

**Decision**: LlmCallEntity 保持 `@GeneratedValue(IDENTITY)` 策略（Long 自增 ID），不改为课件中 `TEXT PRIMARY KEY`。

**Rationale**: 现有实体已使用 IDENTITY 策略且 SQLite 支持 INTEGER PRIMARY KEY AUTOINCREMENT。课件的 `TEXT PRIMARY KEY` 是概念示意，实际 JPA 映射用 Long 自增更简单。不修改已有模式以保持一致性。

**Alternatives considered**:
- 改为 UUID TEXT 主键 — 与现有实体风格不一致，增加复杂度

## R5: Profile 扩展字段

**Decision**: 在现有 `Profile` 类中新增 `model`（String）和 `temperature`（Double）字段，保持 class（不改 record）。

**Rationale**: 现有 Profile 是 mutable class（getter/setter 风格），改为 record 会破坏所有调用方。增量添加字段是最小改动方案。`provider` 字段已存在（对应课件的 provider.name 引用）。

**Alternatives considered**:
- 将 Profile 改为 record — 破坏性变更，影响所有现有调用方
