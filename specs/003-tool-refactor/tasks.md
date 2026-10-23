# Tasks: Tool 模块重构

**Input**: Design documents from `specs/003-tool-refactor/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: 本任务的测试按课件"验收 harness"执行，51 个测试用例覆盖所有改动。

**Organization**: 按 User Story 组织，每个 Story 可独立实现和测试。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件，无依赖）
- **[Story]**: 属于哪个 User Story（US1~US4）
- 包含精确文件路径

## Path Conventions

- oryxos-core: `oryxos-core/src/main/java/com/oryxos/core/`
- oryxos-tool: `oryxos-tool/src/main/java/com/oryxos/tool/`
- 测试: 对应 `src/test/java/` 目录

---

## Phase 1: Setup

**Purpose**: 确认依赖、创建新接口和注解定义

- [x] T001 确认 mvn dependency:tree 中 Spring Boot 3.x + Jackson + JDK HttpClient 存在
- [x] T002 [P] 创建 ConditionalTool 接口 in `oryxos-core/src/main/java/com/oryxos/core/model/ConditionalTool.java`
- [x] T003 [P] 创建 @Tool 注解定义 in `oryxos-tool/src/main/java/com/oryxos/tool/annotation/Tool.java`

**Checkpoint**: 基础接口和注解就绪，User Story 可以开始

---

## Phase 2: Foundational（阻塞所有 User Story）

**Purpose**: ToolExecutor 重构 + ReActLoop 失败隔离——这是所有 User Story 的公共基础

**⚠️ CRITICAL**: 所有 User Story 必须等此阶段完成

### 测试（先写测试，确保 FAIL）

- [x] T004 [P] 创建 ToolExecutorTest in `oryxos-core/src/test/java/com/oryxos/core/react/ToolExecutorTest.java`（10 用例：toolNotFound / success / exceptionCaught / retryableTrue / retryableFalse / noSandboxCheck / auditRecorded / runtimeException / error / null result）
- [x] T005 [P] 创建 ReActLoopRetryableTest in `oryxos-core/src/test/java/com/oryxos/core/react/ReActLoopRetryableTest.java`（3 用例）
- [x] T006 [P] 创建 ReActLoopIsolationTest in `oryxos-core/src/test/java/com/oryxos/core/react/ReActLoopIsolationTest.java`（4 用例）

### 实现

- [x] T007 重构 ToolExecutor：删除 performSandboxCheck() 方法 + 删除 sandbox 字段 + 删除 Sandbox 相关 import in `oryxos-core/src/main/java/com/oryxos/core/react/ToolExecutor.java`
- [x] T008 重构 ReActLoop：for 循环内加 try-catch 防御 + 失败信息拼接 [retryable]/[not retryable] 信号 in `oryxos-core/src/main/java/com/oryxos/core/react/ReActLoop.java`
- [x] T009 运行 `mvn test -pl oryxos-core` 确认 ToolExecutorTest + ReActLoop 测试全绿

**Checkpoint**: ToolExecutor 纯净（无 Sandbox 分发），ReActLoop 失败隔离，7 个测试全绿

---

## Phase 3: User Story 1 — Sandbox 下沉（Priority: P1）🎯 MVP

**Goal**: 每个内置工具在 execute() 第一行自行调用 Sandbox 校验

**Independent Test**: `mvn test -pl oryxos-tool "-Dtest=FileToolsSandboxTest,ShellToolsSandboxTest,HttpToolsSandboxTest,BuiltInToolsSandboxOrderTest"`

### 测试（先写测试，确保 FAIL）

- [x] T010 [P] [US1] 创建 FileToolsSandboxTest in `oryxos-tool/src/test/java/com/oryxos/tool/builtin/FileToolsSandboxTest.java`（5 用例：sandboxCalledFirst / sandboxRejectBlocksIO / writeFileEnforceFileWrite / listDirEnforceFileRead / noSandboxStillWorks）
- [x] T011 [P] [US1] 创建 ShellToolsSandboxTest in `oryxos-tool/src/test/java/com/oryxos/tool/builtin/ShellToolsSandboxTest.java`（3 用例：sandboxCalledFirst / sandboxRejectBlocksExecution / noSandboxStillWorks）
- [x] T012 [P] [US1] 创建 HttpToolsSandboxTest in `oryxos-tool/src/test/java/com/oryxos/tool/builtin/HttpToolsSandboxTest.java`（4 用例：sandboxCalledFirst / sandboxRejectBlocksRequest / httpPostEnforce / noSandboxStillWorks）
- [x] T013 [P] [US1] 创建 BuiltInToolsSandboxOrderTest in `oryxos-tool/src/test/java/com/oryxos/tool/builtin/BuiltInToolsSandboxOrderTest.java`（3 用例：readFile / shell / httpGet InOrder 测试）

### 实现

- [x] T014 [P] [US1] 重构 FileTools：ReadFileTool/WriteFileTool/ListDirTool 各注入 Sandbox（@Autowired(required=false)），execute() 第一行调 sandbox.enforce() in `oryxos-tool/src/main/java/com/oryxos/tool/builtin/FileTools.java`
- [x] T015 [P] [US1] 重构 ShellTools：ShellTool 注入 Sandbox，execute() 第一行调 sandbox.enforce(SHELL_COMMAND) in `oryxos-tool/src/main/java/com/oryxos/tool/builtin/ShellTools.java`
- [x] T016 [P] [US1] 重构 HttpTools：HttpGetTool/HttpPostTool 各注入 Sandbox，execute() 第一行调 sandbox.enforce(HTTP_REQUEST) in `oryxos-tool/src/main/java/com/oryxos/tool/builtin/HttpTools.java`
- [x] T017 [US1] 运行 `mvn test -pl oryxos-tool "-Dtest=FileToolsSandboxTest,ShellToolsSandboxTest,HttpToolsSandboxTest,BuiltInToolsSandboxOrderTest"` 确认 15 个测试全绿

**Checkpoint**: 所有内置工具 Sandbox 自治，15 个测试全绿

---

## Phase 4: User Story 2 — 条件注册 + NotifyTool 独立（Priority: P2）

**Goal**: ConditionalTool 接口生效，NotifyTool 独立为 @Component + ConditionalTool

**Independent Test**: `mvn test -pl oryxos-core,oryxos-tool "-Dtest=ConditionalToolTest,ToolAutoRegistrarTest,NotifyToolTest"`

### 测试（先写测试，确保 FAIL）

- [x] T018 [P] [US2] 创建 ConditionalToolTest in `oryxos-core/src/test/java/com/oryxos/core/model/ConditionalToolTest.java`（3 用例：available / unavailable / nonConditional）
- [x] T019 [P] [US2] 创建 ToolAutoRegistrarTest in `oryxos-tool/src/test/java/com/oryxos/tool/ToolAutoRegistrarTest.java`（2 用例：conditionalRegister / logsSkipped）
- [x] T020 [P] [US2] 创建 NotifyToolTest in `oryxos-tool/src/test/java/com/oryxos/tool/notify/NotifyToolTest.java`（6 用例：channelFound / channelNotFound / defaultFallback / isAvailableTrue / isAvailableFalse / contentBlank）

### 实现

- [x] T021 [US2] 重构 ToolAutoRegistrar：注册时检查 ConditionalTool.isAvailable()，不可用的跳过并记日志 in `oryxos-tool/src/main/java/com/oryxos/tool/ToolAutoRegistrar.java`
- [x] T022 [US2] 创建独立 NotifyTool @Component：实现 OryxTool + ConditionalTool，从 NotifyTools 提取逻辑，isAvailable() 检查渠道表是否为空 in `oryxos-tool/src/main/java/com/oryxos/tool/notify/NotifyTool.java`
- [x] T023 [US2] 删除 NotifyTools.java in `oryxos-tool/src/main/java/com/oryxos/tool/notify/NotifyTools.java`
- [x] T024 [US2] 更新现有 NotifyToolsTest → 删除（NotifyTools 已删除），由 NotifyToolTest 替代
- [x] T025 [US2] 运行 `mvn test -pl oryxos-core,oryxos-tool "-Dtest=ConditionalToolTest,ToolAutoRegistrarTest,NotifyToolTest"` 确认 11 个测试全绿

**Checkpoint**: 条件注册生效，NotifyTool 独立，11 个测试全绿

---

## Phase 5: User Story 3 — @Tool 注解体系（Priority: P3）

**Goal**: Java 开发者用 @Tool 注解标注方法，框架自动扫描注册

**Independent Test**: `mvn test -pl oryxos-tool "-Dtest=ToolMethodScannerTest,AnnotatedToolAdapterTest"`

### 测试（先写测试，确保 FAIL）

- [x] T026 [P] [US3] 创建 AnnotatedToolAdapterTest in `oryxos-tool/src/test/java/com/oryxos/tool/scanner/AnnotatedToolAdapterTest.java`（5 用例：name / description / schema / execute / exception）
- [x] T027 [P] [US3] 创建 ToolMethodScannerTest in `oryxos-tool/src/test/java/com/oryxos/tool/scanner/ToolMethodScannerTest.java`（3 用例：finds / registers / skips）

### 实现

- [x] T028 [US3] 创建 AnnotatedToolAdapter：实现 OryxTool，通过反射调用 @Tool 原方法，execute() 兜底异常返回 ToolResult.fail in `oryxos-tool/src/main/java/com/oryxos/tool/scanner/AnnotatedToolAdapter.java`
- [x] T029 [US3] 创建 ToolMethodScanner：ApplicationRunner，启动时扫描所有 Spring Bean 的 @Tool 方法，包装为 AnnotatedToolAdapter 注册到 ToolRegistry in `oryxos-tool/src/main/java/com/oryxos/tool/scanner/ToolMethodScanner.java`
- [x] T030 [US3] 运行 `mvn test -pl oryxos-tool "-Dtest=ToolMethodScannerTest,AnnotatedToolAdapterTest"` 确认 8 个测试全绿

**Checkpoint**: @Tool 注解扫描注册链路通，8 个测试全绿

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 全量回归 + 代码质量门禁

- [x] T031 运行 `mvn clean verify` 确认全量构建通过（含 Spotless/Checkstyle/SpotBugs/PMD）
- [x] T032 确认前序天（Day 1-4）所有测试回归绿
- [x] T033 自查 H4 六条全局不变量（Sandbox 首行 / 审计落库 / 无明文 key / session_id 隔离 / 无 Reactor / 无 Spring AI 自动执行）
- [x] T034 运行 quickstart.md 中 8 个验证场景

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，立即开始
- **Foundational (Phase 2)**: 依赖 Setup → **阻塞所有 User Story**
- **US1 Sandbox 下沉 (Phase 3)**: 依赖 Foundational
- **US2 条件注册 (Phase 4)**: 依赖 Foundational
- **US3 @Tool 注解 (Phase 5)**: 依赖 Setup（T003 创建了注解定义），可与 Phase 3/4 并行
- **Polish (Phase 6)**: 依赖所有 User Story 完成

### User Story Dependencies

- **US1 (P1)**: Foundational 完成后即可开始，无其他依赖
- **US2 (P2)**: Foundational 完成后即可开始，无其他依赖
- **US3 (P3)**: Setup 完成后即可开始（注解定义 T003），无其他依赖

### Parallel Opportunities

- Phase 1 中 T002 + T003 可并行
- Phase 2 中 T004 + T005 + T006 可并行（不同测试文件）
- Phase 3 中 T010 + T011 + T012 + T013 可并行（不同测试文件）
- Phase 3 中 T014 + T015 + T016 可并行（不同源文件）
- Phase 4 中 T018 + T019 + T020 可并行
- Phase 5 中 T026 + T027 可并行
- Phase 3 + Phase 4 + Phase 5 三个 User Story 可并行（不同文件）

---

## Implementation Strategy

### MVP First (US1 Sandbox 下沉)

1. Phase 1 Setup → Phase 2 Foundational → Phase 3 US1
2. **STOP and VALIDATE**: `mvn test -pl oryxos-tool,oryxos-core` 确认 22+ 个测试全绿
3. 继续 Phase 4 + Phase 5

### Incremental Delivery

1. Setup + Foundational → 基础设施就绪
2. US1 Sandbox 下沉 → 15 个测试绿 → MVP
3. US2 条件注册 → 11 个测试绿
4. US3 @Tool 注解 → 8 个测试绿
5. Polish → 全量 51 个测试绿 + mvn clean verify

---

## Notes

- [P] tasks = 不同文件，可并行
- [Story] label 映射到具体 User Story
- 每个 User Story 可独立完成和测试
- 测试先写，确保 FAIL，再实现
- 每个任务完成后 commit
- 在 checkpoint 停下来验证
