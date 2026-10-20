# Tasks: ReAct 模块——Agent 引擎

**Input**: Design documents from `/specs/003-react-loop-engine/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, quickstart.md

**Tests**: Explicitly requested in feature specification — 6 test classes, 48 acceptance points. Tests MUST be written before or alongside corresponding implementation.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup — 依赖核实与环境确认

**Purpose**: 确认构建环境、Day 1 依赖存在、分支正确

- [X] T001 Run `mvn dependency:tree` to verify Spring AI 1.0.0-M6 BOM dependencies are locked
- [X] T002 Verify Day 1 deliverables exist: `ProviderService`, `ProviderResponse`, `ToolCall`, `Prompt`, `AuditLogger` in codebase
- [X] T003 Confirm on branch `002-day2-react-refactor`

---

## Phase 2: Foundational — 数据模型与基础设施

**Purpose**: 数据模型变更和新基础设施，所有 User Story 的前置依赖

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T004 Add `toolCallId` field (VARCHAR(64)) to `Message.java` in `oryxos-core/src/main/java/com/oryxos/core/model/Message.java`
- [X] T005 Add `toolCallId` field to `ToolInvocationEntity.java` in `oryxos-storage/src/main/java/com/oryxos/storage/entity/ToolInvocationEntity.java` with column `tool_call_id VARCHAR(64)`
- [X] T006 Add `findByToolCallId(String toolCallId)` method to `ToolInvocationRepository.java` in `oryxos-storage/src/main/java/com/oryxos/storage/repository/ToolInvocationRepository.java`
- [X] T007 Create Flyway migration `V5__add_tool_call_id.sql` in `oryxos-storage/src/main/resources/db/migration/sqlite/V5__add_tool_call_id.sql` — `ALTER TABLE tool_invocations ADD COLUMN tool_call_id VARCHAR(64);`
- [X] T008 Create `ProfileContext.java` in `oryxos-core/src/main/java/com/oryxos/core/agent/ProfileContext.java` — ThreadLocal<Profile> with `set()`, `get()`, `clear()` static methods
- [X] T009 Create `AgentConfigException.java` in `oryxos-core/src/main/java/com/oryxos/core/agent/AgentConfigException.java` — extends RuntimeException
- [X] T010 [P] Create `ProfileContextTest.java` in `oryxos-core/src/test/java/com/oryxos/core/agent/ProfileContextTest.java` — 4 acceptance points: set+get, clear→null, override set, thread isolation

**Checkpoint**: Data model ready, ProfileContext + AgentConfigException ready — user story implementation can now begin

---

## Phase 3: User Story 5 — 上下文文件加载与错误约束 (Priority: P2)

**Goal**: ContextLoader 加载 AGENT.md / Bootstrap / Skills，错误约束到位

**Independent Test**: ContextLoaderTest 9 个验收点全绿

### Tests for User Story 5 ⚠️

- [X] T011 [P] [US5] Create `ContextLoaderTest.java` in `oryxos-core/src/test/java/com/oryxos/core/context/ContextLoaderTest.java` — 9 acceptance points: Bootstrap 全存在/部分缺失/全缺失, AGENT.md 正常/不存在/无 frontmatter, Skills 扫描/目录不存在, 无缓存回归

### Implementation for User Story 5

- [X] T012 [US5] Refactor `ContextLoader.java` in `oryxos-core/src/main/java/com/oryxos/core/context/ContextLoader.java` — AGENT.md missing → throw AgentConfigException; Bootstrap missing → log.warn; no caching (read fresh every time); Skills scan from skills/ directory

**Checkpoint**: ContextLoader fully functional with error constraints, 9 tests passing

---

## Phase 4: User Story 3 — 工具执行与全量审计 (Priority: P1)

**Goal**: ToolExecutor 查找→Sandbox→执行→审计（finally 全覆盖）

**Independent Test**: ToolExecutorTest 10 个验收点全绿

### Tests for User Story 3 ⚠️

- [X] T013 [P] [US3] Create `ToolExecutorTest.java` in `oryxos-core/src/test/java/com/oryxos/core/react/ToolExecutorTest.java` — 10 acceptance points: 成功/找不到/Sandbox拒绝/异常, 四种审计全覆盖, 审计含 toolCallId + duration

### Implementation for User Story 3

- [X] T014 [US3] Refactor `ToolExecutor.java` in `oryxos-core/src/main/java/com/oryxos/core/react/ToolExecutor.java` — new signature `execute(Session, ToolCall) → ToolResult`; self-inject ToolRegistry; Sandbox optional inject; audit in finally block covering all 4 scenarios (success/not-found/sandbox-rejected/exception); audit includes toolCallId and duration_ms

**Checkpoint**: ToolExecutor fully functional with audit coverage, 10 tests passing

---

## Phase 5: User Story 2 — Prompt 四段拼接与上下文管理 (Priority: P1)

**Goal**: PromptBuilder 四段拼接（system prompt + memory + 截断历史 + tool schema），含时间戳

**Independent Test**: PromptBuilderTest 9 个验收点全绿

### Tests for User Story 2 ⚠️

- [X] T015 [P] [US2] Create `PromptBuilderTest.java` in `oryxos-core/src/test/java/com/oryxos/core/react/PromptBuilderTest.java` — 9 acceptance points: 四段拼接, 时间戳在末尾, 历史截断, Memory 注入/为空/不存在, Tool schema/无 tools, 传统 switch-case

### Implementation for User Story 2

- [X] T016 [US2] Refactor `PromptBuilder.java` in `oryxos-core/src/main/java/com/oryxos/core/react/PromptBuilder.java` — four-part assembly: ① system prompt (bootstrap + agentBody + skills + current time RFC_1123) ② memory injection via MemoryPort (optional) ③ history truncation by maxHistoryTurns (default 20 turns = 40 messages) ④ tool schema from ToolRegistry; use traditional switch-case for message role mapping (no enhanced switch)

**Checkpoint**: PromptBuilder fully functional with truncation and timestamp, 9 tests passing

---

## Phase 6: User Story 1 — ReAct 循环驱动 Agent 推理 (Priority: P1) 🎯 MVP

**Goal**: ReActLoop 完整 for 循环引擎，对接 ProviderService + PromptBuilder + ToolExecutor

**Independent Test**: ReActLoopTest 8 个验收点全绿

**Dependencies**: Phase 4 (ToolExecutor), Phase 5 (PromptBuilder) must be complete

### Tests for User Story 1 ⚠️

- [X] T017 [P] [US1] Create `ReActLoopTest.java` in `oryxos-core/src/test/java/com/oryxos/core/react/ReActLoopTest.java` — 8 acceptance points: 无 tool_call 直接返回, 有 tool_call 继续循环, 最大轮数强制终止, 用户消息追加, 助手消息每轮追加, 工具结果追加, message=null 不追加, 回归（模型一直调工具直到强制停止）

### Implementation for User Story 1

- [X] T018 [US1] Rewrite `ReActLoop.java` in `oryxos-core/src/main/java/com/oryxos/core/react/ReActLoop.java` — new signature `run(Session, Profile, String message) → ProviderResponse`; dependencies: PromptBuilder, ProviderService, ToolExecutor; for loop with maxIterations; message accumulation (user/assistant/tool); hasToolCalls termination check; no LLM audit (ProviderService handles it); no tools parameter

**Checkpoint**: ReActLoop fully functional, 8 tests passing

---

## Phase 7: User Story 4 — 入口编排与 ProfileContext 生命周期 (Priority: P2)

**Goal**: AgentService.process() 统一入口，ProfileContext 生命周期管理

**Independent Test**: AgentServiceTest 6 个验收点全绿

**Dependencies**: Phase 6 (ReActLoop), Phase 2 (ProfileContext) must be complete

### Tests for User Story 4 ⚠️

- [X] T019 [P] [US4] Create `AgentServiceTest.java` in `oryxos-core/src/test/java/com/oryxos/core/agent/AgentServiceTest.java` — 6 acceptance points: 正常流程, ProfileContext set/clear, Session 持久化, Profile 不存在抛异常, 异常时清理, 回归（请求结束后 ProfileContext 无泄漏）

### Implementation for User Story 4

- [X] T020 [US4] Refactor `AgentService.java` in `oryxos-core/src/main/java/com/oryxos/core/agent/AgentService.java` — new signature `process(Session, String message) → String`; ProfileContext.set → try { reActLoop.run → sessionManager.save → return content } catch { log + rethrow } finally { ProfileContext.clear }; remove LlmPortFactory dependency

**Checkpoint**: AgentService fully functional with lifecycle management, 6 tests passing

---

## Phase 8: Polish — 旧代码清理与跨天回归

**Purpose**: 删除 Day 1 过渡代码，确认无残留引用，全量构建验证

- [X] T021 Delete `LlmPort.java` — replaced by ProviderService
- [X] T022 Delete `LlmPortFactory.java` in `oryxos-core/src/main/java/com/oryxos/core/agent/LlmPortFactory.java` — replaced by ProviderService
- [X] T023 Delete `ProviderLlmPortFactory.java` in `oryxos-provider/` — Day 1 bridge no longer needed
- [X] T024 Delete `ToolCallParser.java` in `oryxos-core/src/main/java/com/oryxos/core/react/ToolCallParser.java` — tool_call extraction now inside ProviderService
- [X] T025 Delete `ToolCallRequest.java` in `oryxos-core/src/main/java/com/oryxos/core/model/ToolCallRequest.java` — replaced by ToolCall record (Day 1)
- [X] T026 Fix any compilation errors caused by deletions — grep for stale references to deleted types across all modules
- [X] T027 Run `mvn clean verify` — full build green: 0 failures, 0 errors, all 48+ tests pass, P3C/SpotBugs/FindSecBugs/PMD clean

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — verify environment
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **US5 ContextLoader (Phase 3)**: Depends on Foundational (AgentConfigException)
- **US3 ToolExecutor (Phase 4)**: Depends on Foundational (data model)
- **US2 PromptBuilder (Phase 5)**: Depends on Foundational + US5 (ContextLoader for system prompt)
- **US1 ReActLoop (Phase 6)**: Depends on US3 (ToolExecutor) + US2 (PromptBuilder)
- **US4 AgentService (Phase 7)**: Depends on US1 (ReActLoop) + Foundational (ProfileContext)
- **Polish (Phase 8)**: Depends on all user stories complete

### User Story Dependencies

```
US5 (ContextLoader) ──→ US2 (PromptBuilder) ──┐
                                               ├──→ US1 (ReActLoop) ──→ US4 (AgentService)
US3 (ToolExecutor) ────────────────────────────┘
```

### Parallel Opportunities

- Phase 3 (US5) and Phase 4 (US3) can run in parallel — different files, no cross-dependency
- Phase 5 (US2) depends on Phase 3 (US5) for ContextLoader
- Phase 6 (US1) depends on both Phase 4 and Phase 5
- Test files within each phase can be written in parallel with implementation of other phases

---

## Parallel Example: Phase 3 + Phase 4

```text
# These can run in parallel:
Task T011: ContextLoaderTest (US5) — different file from ToolExecutorTest
Task T013: ToolExecutorTest (US3) — different file from ContextLoaderTest

# After both complete:
Task T012: ContextLoader impl (US5)
Task T014: ToolExecutor impl (US3)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 3-6 in dependency order (US5 → US3 → US2 → US1)
4. **STOP and VALIDATE**: ReActLoopTest 8/8 passing
5. Then continue to US4 + Polish

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. US5 (ContextLoader) → 9 tests passing
3. US3 (ToolExecutor) → 10 tests passing
4. US2 (PromptBuilder) → 9 tests passing
5. US1 (ReActLoop) → 8 tests passing → **MVP checkpoint**
6. US4 (AgentService) → 6 tests passing
7. Polish → cleanup + full build green → **48/48 tests passing**

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks in other phases
- [Story] label maps task to specific user story for traceability
- Each user story is independently completable and testable after its phase
- Tests MUST be written before or alongside implementation (harness first)
- All code in English, comments in Chinese
- Traditional switch-case only (no enhanced switch `case ->`)
- maxIterations default 10, maxHistoryTurns default 20
- Prompt must include current datetime in RFC_1123 format at the end
