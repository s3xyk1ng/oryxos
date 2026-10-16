# Tasks: Provider 模块——LLM 协议翻译层

**Input**: Design documents from `/specs/002-provider-module/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/provider-api.md

**Tests**: 课件"验收 harness"明确要求 6 个测试类，全部必须落地。测试先行。

**Organization**: 按 User Story 分组，harness 先行。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1=LLM 调用, US2=多 Provider 注册, US3=审计, US4=Tool Schema

---

## Phase 1: Setup — 数据模型与接口定义（基础层）

**Purpose**: 在 oryxos-core 定义所有接口和数据模型，在 oryxos-storage 扩展审计实体。这是所有 User Story 的前置条件。

- [ ] T001 新增 ProviderDef record in `oryxos-core/src/main/java/com/oryxos/core/model/ProviderDef.java`（字段：name, type, apiKey, baseUrl, fallbacks）
- [ ] T002 [P] 新增 ToolCall record in `oryxos-core/src/main/java/com/oryxos/core/model/ToolCall.java`（字段：id, name, arguments）
- [ ] T003 [P] 新增 Usage record in `oryxos-core/src/main/java/com/oryxos/core/model/Usage.java`（字段：promptTokens, completionTokens, totalTokens）
- [ ] T004 新增 ProviderResponse record in `oryxos-core/src/main/java/com/oryxos/core/model/ProviderResponse.java`（字段：content, toolCalls, usage；含 hasToolCalls() 方法）
- [ ] T005 新增 Prompt record in `oryxos-core/src/main/java/com/oryxos/core/model/Prompt.java`（字段：messages: List\<Message\>, tools: List\<OryxTool\>）
- [ ] T006 [P] 新增 ProviderNotFoundException in `oryxos-core/src/main/java/com/oryxos/core/model/ProviderNotFoundException.java`
- [ ] T007 [P] 新增 ProviderCallException in `oryxos-core/src/main/java/com/oryxos/core/model/ProviderCallException.java`
- [ ] T008 新增 ProviderService interface in `oryxos-core/src/main/java/com/oryxos/core/agent/ProviderService.java`（方法：chat(sessionId, profile, prompt) → ProviderResponse）
- [ ] T009 新增 ProviderRegistry interface in `oryxos-core/src/main/java/com/oryxos/core/agent/ProviderRegistry.java`（方法：save(ProviderDef), find(name) → ProviderDef）
- [ ] T010 扩展 Profile 新增 model(String) + temperature(Double) 字段及 getter/setter in `oryxos-core/src/main/java/com/oryxos/core/model/Profile.java`
- [ ] T011 扩展 LlmCallEntity 新增 agentName/status/toolCallCount/errorMessage 字段及 getter/setter in `oryxos-storage/src/main/java/com/oryxos/storage/entity/LlmCallEntity.java`

**Checkpoint**: 接口与数据模型就绪，可以开始实现。

---

## Phase 2: US2 — 多 Provider 显式注册与配置分层 (Priority: P2)

**Goal**: Provider 注册表能从 YAML 配置加载 ProviderDef，按 name 查找，找不到抛异常。

**Independent Test**: InMemoryProviderRegistryTest + ProviderBootstrapTest

### Tests（先行）

- [ ] T012 [US2] 新增 InMemoryProviderRegistryTest in `oryxos-provider/src/test/java/com/oryxos/provider/InMemoryProviderRegistryTest.java`（save_and_find + find_not_found 两个用例）
- [ ] T013 [US2] 新增 ProviderBootstrapTest in `oryxos-provider/src/test/java/com/oryxos/provider/ProviderBootstrapTest.java`（load_from_yaml 用例）

### Implementation

- [ ] T014 [US2] 新增 InMemoryProviderRegistry in `oryxos-provider/src/main/java/com/oryxos/provider/InMemoryProviderRegistry.java`（实现 ProviderRegistry，ConcurrentHashMap 存储）
- [ ] T015 [US2] 新增 ProvidersProperties in `oryxos-provider/src/main/java/com/oryxos/provider/ProvidersProperties.java`（@ConfigurationProperties("oryxos")，List\<ProviderDef\> providers）
- [ ] T016 [US2] 新增 ProviderBootstrap in `oryxos-provider/src/main/java/com/oryxos/provider/ProviderBootstrap.java`（ApplicationRunner，从 ProvidersProperties 加载到 Registry）
- [ ] T017 [US2] 新增 ProviderAutoConfiguration in `oryxos-provider/src/main/java/com/oryxos/provider/ProviderAutoConfiguration.java`（组装 InMemoryProviderRegistry + ProviderBootstrap Bean）

**Checkpoint**: Provider 注册表可独立运行，save/find/YAML 加载全通。

---

## Phase 3: US1 — ReAct 循环调用 LLM 获取响应 (Priority: P1) MVP

**Goal**: ProviderService.chat() 能通过 ProviderRegistry 查找 Provider，构建 ChatModel（指纹缓存），调用 LLM 返回原始结果。

**Independent Test**: ProviderChatModelFactoryTest + SpringAiProviderServiceImplTest（chat 成功/失败/provider 不存在/ChatOptions 合并）

### Tests（先行）

- [ ] T018 [P] [US1] 新增 ProviderChatModelFactoryTest in `oryxos-provider/src/test/java/com/oryxos/provider/ProviderChatModelFactoryTest.java`（resolve_first_time + resolve_cached + resolve_config_changed 三个用例）
- [ ] T019 [US1] 新增 SpringAiProviderServiceImplTest in `oryxos-provider/src/test/java/com/oryxos/provider/SpringAiProviderServiceImplTest.java`（chat_success_no_tools + chat_success_with_tools + chat_provider_not_found + chat_llm_call_failed + chat_options_merged 五个用例）

### Implementation

- [ ] T020 [US1] 新增 ProviderChatModelFactory in `oryxos-provider/src/main/java/com/oryxos/provider/ProviderChatModelFactory.java`（指纹缓存 fingerprint=apiKey|baseUrl，按 type switch 构建 ChatModel：openai→OpenAiChatModel, qwen/openai-compatible→OpenAiChatModel+baseUrl, azure-openai→TODO 预留）
- [ ] T021 [US1] 新增 SpringAiProviderServiceImpl in `oryxos-provider/src/main/java/com/oryxos/provider/SpringAiProviderServiceImpl.java`（实现 ProviderService.chat()：registry.find → factory.resolve → buildOptions → chatModel.call → 提取结果）
- [ ] T022 [US1] 实现 buildOptions() 按 provider type switch 构建 ChatOptions in `SpringAiProviderServiceImpl.java`（openai→OpenAiChatOptions, azure-openai→AzureOpenAiChatOptions, qwen/openai-compatible→OpenAiChatOptions+baseUrl；合并 Profile 的 model/temperature）

**Checkpoint**: chat() 核心流程跑通，mock ChatModel 验证成功/失败/不存在/选项合并。

---

## Phase 4: US4 — Tool Schema 转换 (Priority: P3)

**Goal**: OryxTool 转为 Spring AI FunctionCallback（只生成 schema），从 ChatResponse 提取原始 tool_calls。

**Independent Test**: ToolSchemaAdapterTest

### Tests（先行）

- [ ] T023 [US4] 新增 ToolSchemaAdapterTest in `oryxos-provider/src/test/java/com/oryxos/provider/ToolSchemaAdapterTest.java`（toFunctionCallback_success + extractToolCalls_empty + extractToolCalls_multiple 三个用例）

### Implementation

- [ ] T024 [US4] 新增 ToolSchemaAdapter in `oryxos-provider/src/main/java/com/oryxos/provider/ToolSchemaAdapter.java`（toFunctionCallback: OryxTool→FunctionCallback，占位 function 抛 UnsupportedOperationException；extractToolCalls: ChatResponse→List\<ToolCall\>）
- [ ] T025 [US4] 集成 ToolSchemaAdapter 到 SpringAiProviderServiceImpl（chat() 中将 prompt.getTools() 转为 FunctionCallback 列表传入 ChatOptions，调用后提取 toolCalls 填入 ProviderResponse）

**Checkpoint**: Tool schema 转换正确，chat() 端到端含 tool 场景通过。

---

## Phase 5: US3 — LLM 调用审计全记录 (Priority: P2)

**Goal**: 每次 LLM 调用（成功/失败）写入 llm_calls 表，字段完整。

**Independent Test**: SpringAiProviderServiceImplTest 中审计断言已覆盖（T019 中验证审计 status=SUCCESS/FAILED）

### Implementation

- [ ] T026 [US3] 新增 AuditLogger in `oryxos-provider/src/main/java/com/oryxos/provider/AuditLogger.java`（logSuccess + logFailure 方法，写入 LlmCallRepository；字段：sessionId, agentName, providerName, model, status, promptTokens, completionTokens, totalTokens, toolCallCount, durationMs, errorMessage, createdAt）
- [ ] T027 [US3] 集成 AuditLogger 到 SpringAiProviderServiceImpl（chat() 成功路径调 logSuccess，catch 路径调 logFailure，确保异常也记录 durationMs 和 errorMessage）
- [ ] T028 [US3] 更新 ProviderAutoConfiguration 注入 LlmCallRepository 到 AuditLogger（确保 oryxos-provider 依赖 oryxos-storage）

**Checkpoint**: 成功/失败双路径审计写入验证通过。

---

## Phase 6: 集成冒烟 + 清理

**Purpose**: 端到端冒烟测试，清理旧代码，构建门禁验证。

- [ ] T029 新增 ProviderSmokeTest (@Tag("integration")) in `oryxos-provider/src/test/java/com/oryxos/provider/ProviderSmokeTest.java`（smoke_full_flow：YAML 配置 → Registry → ChatModel 构建 → mock 调用 → 审计表有记录）
- [ ] T030 清理旧代码：删除或重构 oryxos-provider 中已有的 ProviderService.java、ProviderConfig.java、FunctionCallingAdapter.java、LlmPortImpl.java、LlmPortFactoryImpl.java（确保不破坏 oryxos-core 中 ReActLoop 等调用方）
- [ ] T031 更新 oryxos-provider/pom.xml 确保依赖包含 oryxos-storage（AuditLogger 需要 LlmCallRepository）
- [ ] T032 运行 `mvn clean verify` 全绿（含 Spotless/Checkstyle/SpotBugs/PMD），修复所有门禁问题

**Checkpoint**: 全部测试通过，构建门禁全绿，旧代码清理完毕。

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: 无依赖，立即开始
- **Phase 2 (US2)**: 依赖 Phase 1 完成
- **Phase 3 (US1)**: 依赖 Phase 1 + Phase 2（需要 ProviderRegistry）
- **Phase 4 (US4)**: 依赖 Phase 3（需要 SpringAiProviderServiceImpl）
- **Phase 5 (US3)**: 依赖 Phase 3（集成到 chat() 流程）
- **Phase 6 (Polish)**: 依赖所有 US 完成

### 关键路径

```
Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6
                      (MVP)
```

### Parallel Opportunities

- T001-T010 中标记 [P] 的可并行
- T012/T013 测试可并行
- T018/T019 测试可并行

---

## Implementation Strategy

### MVP First (Phase 1-3)

1. Phase 1: 数据模型 + 接口
2. Phase 2: Provider 注册表
3. Phase 3: chat() 核心流程
4. **STOP and VALIDATE**: 跑 SpringAiProviderServiceImplTest + ProviderChatModelFactoryTest

### Incremental Delivery

1. Setup + Registry → Provider 可注册可查找
2. + chat() → LLM 可调用（MVP!）
3. + ToolSchema → 工具 schema 可转换
4. + Audit → 审计全记录
5. + Smoke → 端到端验证

---

## Notes

- 所有测试方法名用英文，课件原文用 @DisplayName 保留
- 避开 P3C/ASM 解析不了的 Java 18+ 语法（switch expression 用传统 switch-case 或 if-else 替代）
- 语法禁区：不用 `default ->` 增强 switch，用 `default:` 传统写法
- Azure OpenAI 分支预留 TODO，不引入依赖
- 凭证全部走环境变量，不明文硬编码
