# Tasks: Notify 通知推送模块

**Input**: Design documents from `/specs/002-day4-notify/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, quickstart.md

**Tests**: 课件"验收 harness"明确要求 14 个测试用例，测试任务包含在内。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup（无新增，复用已有基础设施）

已有基础设施无需修改：
- `notify_channels` 表已由 V2 迁移脚本创建
- `NotifyChannelEntity` / `NotifyChannelRepository` 已存在
- `NotifyChannelAdapter` / `WebhookNotifyAdapter` / `NotifyTools` 骨架已存在（需重构）
- Jackson 2.18.1 已在 BOM 中确认

**无需 setup 任务，直接进入 Phase 2。**

---

## Phase 2: Foundational（阻塞所有 User Story 的前置任务）

**Purpose**: 创建所有 User Story 共用的基础数据结构和接口

**⚠️ CRITICAL**: 此阶段完成前不得开始任何 User Story

- [x] T001 新建 `NotifyTarget` record（type: String, config: Map<String, String>）在 `oryxos-tool/src/main/java/com/oryxos/tool/notify/NotifyTarget.java`
- [x] T002 重构 `NotifyChannelAdapter` 接口：去掉 `getType()` 方法，`send` 入参从 `(String target, String content)` 改为 `(NotifyTarget target, String content)` 在 `oryxos-tool/src/main/java/com/oryxos/tool/notify/NotifyChannelAdapter.java`
- [x] T003 [P] Profile 新增 `defaultNotifyChannel` 字段（String 类型，默认 null）+ getter/setter 在 `oryxos-core/src/main/java/com/oryxos/core/model/Profile.java`
- [x] T004 AgentLoader.deriveProfile() 新增解析 `default_notify_channel` frontmatter key → `profile.setDefaultNotifyChannel(...)` 在 `oryxos-core/src/main/java/com/oryxos/core/agent/AgentLoader.java`

**Checkpoint**: NotifyTarget + 接口 + Profile 字段就绪，可以开始 User Story 实现

---

## Phase 3: User Story 1 — 通用 Webhook 适配器（Priority: P1）🎯 MVP

**Goal**: WebhookNotifyAdapter 按新设计重构，接口中立，5xx 向上抛，URL 来自 target

**Independent Test**: mock HttpServer 验证 POST body 含 content、URL 来自 target、5xx 抛异常

### Tests for User Story 1 ⚠️

> **先写测试，确保测试在实现前失败（红 → 绿）**

- [x] T005 [P] [US1] 新建 `WebhookNotifyAdapterTest`：`send_success` — mock HttpServer 返回 200，断言返回 "Notification sent successfully (HTTP 200)"，断言收到的 POST body 是 JSON 且包含 content 字段 在 `oryxos-tool/src/test/java/com/oryxos/tool/notify/WebhookNotifyAdapterTest.java`
- [x] T006 [P] [US1] `WebhookNotifyAdapterTest`：`send_typeMismatch` — type=smtp 的 NotifyTarget，断言返回 "unsupported type: smtp"，断言 mock server 未收到任何请求 在 `oryxos-tool/src/test/java/com/oryxos/tool/notify/WebhookNotifyAdapterTest.java`
- [x] T007 [P] [US1] `WebhookNotifyAdapterTest`：`send_urlMissing` — type=webhook 但 config 无 url，断言抛 IllegalArgumentException 在 `oryxos-tool/src/test/java/com/oryxos/tool/notify/WebhookNotifyAdapterTest.java`
- [x] T008 [P] [US1] `WebhookNotifyAdapterTest`：`send_http5xx` — mock server 返回 500，断言抛 RuntimeException（包含状态码信息） 在 `oryxos-tool/src/test/java/com/oryxos/tool/notify/WebhookNotifyAdapterTest.java`
- [x] T009 [P] [US1] `WebhookNotifyAdapterTest`：`send_urlComesFromTarget` — 用两个不同 url 的 NotifyTarget 分别发送，断言 POST 请求发往不同路径（URL 来自 target 不是硬编码） 在 `oryxos-tool/src/test/java/com/oryxos/tool/notify/WebhookNotifyAdapterTest.java`

### Implementation for User Story 1

- [x] T010 [US1] 重构 `WebhookNotifyAdapter`：send 入参改为 NotifyTarget；type 不匹配返回 "unsupported type: xxx"；url 缺失抛 IllegalArgumentException；用 Jackson ObjectMapper 构建 payload（`{"msgtype":"text","text":{"content":"..."}}`）；非 2xx 抛 RuntimeException；IOException 包装为 RuntimeException 上抛；留 Sandbox TODO 占位注释 在 `oryxos-tool/src/main/java/com/oryxos/tool/notify/WebhookNotifyAdapter.java`

**Checkpoint**: WebhookNotifyAdapter 全部 5 个测试通过

---

## Phase 4: User Story 2 — NotifyTools 编排（Priority: P1）

**Goal**: NotifyTools 从 Repository 查渠道、构建 NotifyTarget、遍历适配器、默认渠道 fallback、错误不静默

**Independent Test**: mock Repository + mock Adapter 验证渠道不存在报错、正常发送、无匹配适配器、参数校验、默认渠道

### Tests for User Story 2 ⚠️

- [x] T011 [P] [US2] 新建 `NotifyToolsTest`：`execute_channelNotFound` — mock repo.findByName 返回 empty，断言 ToolResult.fail 包含 "Channel not found" 在 `oryxos-tool/src/test/java/com/oryxos/tool/notify/NotifyToolsTest.java`
- [x] T012 [P] [US2] `NotifyToolsTest`：`execute_success` — mock repo 返回 entity + mock adapter 返回成功，断言 ToolResult.ok 在 `oryxos-tool/src/test/java/com/oryxos/tool/notify/NotifyToolsTest.java`
- [x] T013 [P] [US2] `NotifyToolsTest`：`execute_noMatchingAdapter` — type=smtp，只有 webhook adapter，断言 ToolResult.fail 包含 "No adapter supports" 在 `oryxos-tool/src/test/java/com/oryxos/tool/notify/NotifyToolsTest.java`
- [x] T014 [P] [US2] `NotifyToolsTest`：`execute_contentBlank` — content=""，断言 ToolResult.fail 包含 "content parameter is required" 在 `oryxos-tool/src/test/java/com/oryxos/tool/notify/NotifyToolsTest.java`
- [x] T015 [P] [US2] `NotifyToolsTest`：`execute_defaultChannelFallback` — LLM 不传 channel，mock ProfileContext 返回 defaultNotifyChannel="team-lark"，断言使用 "team-lark" 查询 repo 在 `oryxos-tool/src/test/java/com/oryxos/tool/notify/NotifyToolsTest.java`

### Implementation for User Story 2

- [x] T016 [US2] 重构 `NotifyTools`：注入 `NotifyChannelRepository`；execute() 内——① 参数校验 content 非空 ② channel 为空时从 ProfileContext 取 defaultNotifyChannel ③ findByName 查渠道 ④ buildTarget 构建 NotifyTarget（含 TODO: ${ENV_VAR} 解析占位）⑤ TODO: Sandbox 白名单占位 ⑥ 遍历 adapters 谁支持谁处理 ⑦ 全部 unsupported 返回 fail；input schema 中 channel 改为非 required 在 `oryxos-tool/src/main/java/com/oryxos/tool/notify/NotifyTools.java`

**Checkpoint**: NotifyTools 全部 5 个测试通过

---

## Phase 5: User Story 3 — Profile 默认渠道解析（Priority: P2）

**Goal**: AgentLoader 正确解析 frontmatter 中的 default_notify_channel

**Independent Test**: 构造含/不含 default_notify_channel 的 YAML，验证 deriveProfile 结果

### Tests for User Story 3 ⚠️

- [x] T017 [P] [US3] 新建 `AgentLoaderNotifyTest`：`deriveProfile_withDefaultNotifyChannel` — frontmatter 含 `default_notify_channel: team-lark`，断言 profile.getDefaultNotifyChannel() == "team-lark" 在 `oryxos-core/src/test/java/com/oryxos/core/agent/AgentLoaderNotifyTest.java`
- [x] T018 [P] [US3] `AgentLoaderNotifyTest`：`deriveProfile_withoutDefaultNotifyChannel` — frontmatter 不含该字段，断言 profile.getDefaultNotifyChannel() == null，不报错 在 `oryxos-core/src/test/java/com/oryxos/core/agent/AgentLoaderNotifyTest.java`

### Implementation for User Story 3

> T003（Profile 字段）和 T004（AgentLoader 解析）已在 Foundational 阶段完成。此阶段只需确保测试通过。

- [x] T019 [US3] 验证 T003 + T004 实现满足 T017/T018 测试，如有不足则修补

**Checkpoint**: AgentLoader 2 个 frontmatter 解析测试通过

---

## Phase 6: User Story 4 — 工具注册控制（Priority: P2）

**Goal**: notify_channels 表为空时不注册 notify 工具，LLM 看不到

**Independent Test**: mock Repository count=0，验证 ToolRegistry 中无 notify 工具

### Implementation for User Story 4

- [x] T020 [US4] 实现工具注册控制逻辑：NotifyTools 初始化时检查 channelRepository.count()，若为 0 则不注册 NotifyTool（日志输出 "No notify channels configured, notify tool will not be available"）。具体方案：NotifyTool 不直接 @Component，由 NotifyTools 在 @PostConstruct 中根据渠道数量决定是否创建并注册 在 `oryxos-tool/src/main/java/com/oryxos/tool/notify/NotifyTools.java`

**Checkpoint**: 渠道表为空时 notify 工具不注册

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 全量回归 + 验证

- [x] T021 全量回归测试：`mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD），0 failures 在 project root
- [x] T022 课件 harness 映射核对：确认 14 个测试用例逐个对应课件"验收 harness"表格中的关键回归点
- [x] T023 本节交付物存在性核对：逐项 grep/ls 确认 6 个源文件 + 3 个测试文件全部存在且非空
- [x] T024 H4 六条全局不变量自查（涉外 IO 首行过 Sandbox 占位 / 审计落库 / grep 无明文 key / session_id 不越界 / 无 Reactor / 无 Spring AI 自动工具执行）
- [x] T025 按 quickstart.md 执行验证（mock webhook 端到端 + 错误场景）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 2 (Foundational)**: 无依赖，最先执行
- **Phase 3 (US1 Webhook)**: 依赖 Phase 2（NotifyTarget + 接口重构完成）
- **Phase 4 (US2 NotifyTools)**: 依赖 Phase 2 + Phase 3（需要重构后的 WebhookNotifyAdapter）
- **Phase 5 (US3 Profile)**: 依赖 Phase 2（T003/T004 已在 Foundational 完成，此阶段只验证测试）
- **Phase 6 (US4 注册控制)**: 依赖 Phase 4（NotifyTools 重构完成后才能控制注册）
- **Phase 7 (Polish)**: 依赖全部 User Story 完成

### Within Each User Story

- 测试先于实现（harness 先行）
- 接口/record 先于适配器
- 适配器先于编排

### Parallel Opportunities

- T005~T009（US1 的 5 个测试）可并行编写
- T011~T015（US2 的 5 个测试）可并行编写
- T017~T018（US3 的 2 个测试）可并行编写
- T003 + T004（Foundational）可并行编写

---

## Implementation Strategy

### MVP First (US1 Only)

1. Phase 2: Foundational（NotifyTarget + 接口 + Profile 字段）
2. Phase 3: US1（WebhookNotifyAdapter + 5 个测试）
3. **STOP and VALIDATE**: `mvn test -pl oryxos-tool -Dtest=WebhookNotifyAdapterTest`

### Incremental Delivery

1. Foundational → 基础就绪
2. US1 → Webhook 适配器可用 → 测试通过
3. US2 → NotifyTools 编排可用 → 测试通过
4. US3 → frontmatter 解析可用 → 测试通过
5. US4 → 注册控制可用
6. Polish → 全量回归绿

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- 测试方法名必须英文，@DisplayName 保留课件中文原文
- 代码全英文，注释中文
- 不静默失败，校验在前发送在后
