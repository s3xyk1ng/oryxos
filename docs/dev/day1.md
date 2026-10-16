# Provider 模块设计文档

> 文档日期：2026-10-13
> 模块名称：oryxos-provider
> 状态：设计冻结，待授权编码

---

## 1. 模块是什么，干嘛用的

### 一句话定位

Provider 模块是 OryxOS 的 **LLM 协议翻译层**——把 OryxOS 内部的统一调用请求翻译成各家 LLM 厂商的协议格式，把响应翻译回统一格式。

### 在系统中的位置

```
                    ┌─────────────────────────────────────────┐
                    │            oryxos-core                   │
                    │  ReActLoop ──► ProviderService.chat()    │
                    └──────────────┬──────────────────────────┘
                                   │
                    ┌──────────────▼──────────────────────────┐
                    │          oryxos-provider                 │
                    │  SpringAiProviderServiceImpl             │
                    │    ├─ ProviderRegistry（查映射）          │
                    │    ├─ ProviderChatModelFactory（构建实例）│
                    │    ├─ ToolSchemaAdapter（格式转换）       │
                    │    └─ AuditLogger（写审计表）             │
                    └──────────────┬──────────────────────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              ▼                    ▼                    ▼
        OpenAI / Azure      阿里云 Qwen          其他厂商...
       (ChatModel)         (ChatModel)         (ChatModel)
```

### 上下游关系

| 方向 | 对接方 | 说明 |
|------|--------|------|
| 上游（谁调用我） | `ReActLoop`（oryxos-core） | Agent 推理循环需要 LLM 响应时调用 `ProviderService.chat()` |
| 下游（我调用谁） | Spring AI `ChatModel` 实现 | 通过 Spring AI 的 Provider 抽象调用各家 LLM API |
| 配置来源 | `application.yaml` + Agent Profile | 全局连接池 + Agent 级消费策略 |
| 审计出口 | `llm_calls` 表（oryxos-storage） | 每次调用写入审计记录 |

### 核心职责（5 条）

1. **协议翻译**：把统一的 `Prompt` 翻译成各家 LLM 的协议格式
2. **连接管理**：管理多个 Provider 的连接实例（ChatModel），支持指纹缓存
3. **配置分层**：全局层声明连接资源池，Profile 层声明使用策略
4. **审计记录**：每次 LLM 调用（成功/失败）写入 `llm_calls` 表
5. **Tool Schema 适配**：把 `OryxTool` 的 JSON Schema 翻译成 Spring AI 的 FunctionCallback

### 模块不负责的事

- **不执行 Tool**：tool_calls 原样返回给 ReActLoop，Provider 层不碰执行逻辑
- **不做编排**：不做多轮对话管理、不做 Agent 协作
- **不做自动重试/熔断**：核心阶段不实现，已预留扩展点

---

## 2. 动手之前先想清楚几件事

### 2.1 两层配置分离

**决策**：全局层（资源池）和 Profile 层（消费策略）分离。

| 层级 | 配置文件 | 内容 | 类比 |
|------|----------|------|------|
| 全局层 | `application.yaml` → `oryxos.providers[]` | name + type + baseUrl + apiKey | 公司买了哪些模型服务 |
| Profile 层 | `.oryxos/profiles/<agent>.yaml` | provider.name + model + temperature | 每个 Agent 用哪个、怎么用 |

**为什么这样分**：
- 全局层管「有什么」——运维配一次，所有 Agent 共享
- Profile 层管「怎么用」——每个 Agent 可以选不同的模型和参数
- 合并点在运行时的 `buildOptions()`：连接信息来自 ProviderDef，model/temperature 来自 Profile

### 2.2 Spring AI 边界（不可协商）

**决策**：只用 Spring AI 的协议转换能力，禁用一切自动执行机制。

| 允许用 | 禁止用 |
|--------|--------|
| `ChatModel.call(Prompt)` | `ChatClient.call()`（会自动执行 tool） |
| Spring AI 的 FunctionCallback schema 生成 | Spring AI 的 Advisor 链 |
| 各家 ChatModel 实现类（OpenAiChatModel 等） | Spring AI 的 Agent 抽象 |

**为什么**：agents.md 不可协商原则 #1 和 #2。OryxOS 的 ReAct 循环必须自己实现，tool 调度必须自己控制。如果用 ChatClient，tool 会被 Spring AI 自动执行一次，OryxOS 又执行一次，导致重复调用。

**避坑手段**：第一个验收测试就检查返回的是原始 tool_calls 还是执行后结果。

### 2.3 显式映射 vs 自动发现

**决策**：用显式 `Map<String, ProviderDef>` 映射，不靠类型扫描。

```java
ProviderDef def = providerRegistry.find("qwen-plus");
// 找不到就抛 ProviderNotFoundException，没有隐式降级
```

**为什么**：
- 显式映射让配置可审计、可追踪
- 避免 Spring 的自动注入带来的不确定性
- Agent 配了一个不存在的 provider，立刻报错，而不是悄悄 fallback

### 2.4 指纹缓存机制

**决策**：ChatModel 实例用指纹缓存，配置变了自动重建。

```
fingerprint = apiKey + "|" + baseUrl
```

| 场景 | 行为 |
|------|------|
| 首次调用 | 构建 ChatModel，缓存 fingerprint + 实例 |
| 配置未变 | 直接复用缓存实例 |
| apiKey 或 baseUrl 变了 | 原地重建 ChatModel，更新 fingerprint |

**为什么**：ChatModel 构建有开销（连接池初始化等），不能每次调用都重建。但配置热更新后必须用新配置，所以需要指纹检测变化。

### 2.5 ChatOptions 类型不统一问题

**决策**：`buildOptions()` 方法按 provider type switch 构建对应类型的 ChatOptions。

| Provider Type | ChatOptions 类型 |
|---------------|------------------|
| openai | `OpenAiChatOptions` |
| azure-openai | `AzureOpenAiChatOptions` |
| qwen | 通过 OpenAI 兼容协议，用 `OpenAiChatOptions` |
| openai-compatible | `OpenAiChatOptions` + 自定义 baseUrl |

**为什么**：Spring AI 各家实现的 ChatOptions 不是统一接口，必须用对应类型才能正确传递参数。

### 2.6 已知坑和避坑手段

| 坑 | 手段 |
|---|------|
| Spring AI 隐式自动执行 tool | 不用 ChatClient；第一个验证看返回的是原始 tool_calls 还是执行后结果 |
| ChatOptions 类型不统一 | buildOptions() 按 type switch 构建对应类型 |
| OpenAI 兼容协议非内置 | type=openai-compatible 走 OpenAiChatModel + 自定义 baseUrl |
| Spring AI M6 预发布 API 不稳 | 锁版本 1.0.0-M6；Spring AI 调用集中在 ToolSchemaAdapter + SpringAiProviderServiceImpl，缩小影响面 |
| 各家 function calling 支持不同 | ProviderDef 预留 capabilities 字段，未来可按能力过滤 |

---

## 3. 代码怎么写

### 3.1 核心类清单

#### oryxos-core（接口层）

| 类名 | 类型 | 职责 |
|------|------|------|
| `ProviderService` | interface | 对外唯一门面：`chat(sessionId, profile, prompt)` |
| `ProviderRegistry` | interface | Provider 注册表：`save(ProviderDef)` / `find(name)` |
| `ProviderDef` | record | Provider 定义：name + type + apiKey + baseUrl + fallbacks |
| `ProviderResponse` | record | LLM 响应：content + toolCalls + usage，含 `hasToolCalls()` |
| `ToolCall` | record | Tool 调用：id + name + arguments（JSON 字符串） |
| `Usage` | record | Token 用量：promptTokens + completionTokens + totalTokens |
| `Prompt` | record | 请求：messages + tools（OryxTool 列表） |
| `ProviderNotFoundException` | exception | Provider 不存在时抛出 |
| `ProviderCallException` | exception | LLM 调用失败时抛出 |

#### oryxos-provider（实现层）

| 类名 | 类型 | 职责 |
|------|------|------|
| `SpringAiProviderServiceImpl` | class | ProviderService 核心实现 |
| `ToolSchemaAdapter` | class | OryxTool → Spring AI FunctionCallback 转换 + extractToolCalls() |
| `AuditLogger` | class | 写 llm_calls 审计表（成功和失败都记录） |
| `ProviderChatModelFactory` | class | 按 ProviderDef.type 构建 ChatModel + 指纹缓存 |
| `InMemoryProviderRegistry` | class | ProviderRegistry 的内存 Map 实现（TODO: 未来重构为 SQLite） |
| `ProviderBootstrap` | class | ApplicationRunner，启动时从 YAML 加载到 Registry |
| `ProvidersProperties` | class | `@ConfigurationProperties("oryxos")` 绑定全局 YAML |
| `ProviderAutoConfiguration` | class | Spring 自动配置，组装 Bean |
| `LlmCallEntity` | class | JPA 实体，映射 llm_calls 表 |
| `LlmCallRepository` | interface | Spring Data JPA Repository |

### 3.2 核心方法：chat() 流程

```java
ProviderResponse chat(String sessionId, Profile profile, Prompt prompt) {
    // 1. 查 Provider 定义
    ProviderDef def = registry.find(profile.getProviderName());
    //    找不到 → throw ProviderNotFoundException

    // 2. 获取/构建 ChatModel（指纹缓存）
    ChatModel chatModel = factory.resolve(def);

    // 3. 构建 ChatOptions（按 type switch）
    ChatOptions options = buildOptions(def, profile, prompt.getTools());

    // 4. 调用 LLM
    long start = System.currentTimeMillis();
    try {
        var response = chatModel.call(new Prompt(prompt.getMessages(), options));

        // 5. 提取结果
        String content = extractContent(response);
        List<ToolCall> toolCalls = toolSchemaAdapter.extractToolCalls(response);
        Usage usage = extractUsage(response);

        // 6. 审计记录（成功）
        long duration = System.currentTimeMillis() - start;
        audit.logSuccess(sessionId, profile.getAgentName(), def.getName(),
                         profile.getModel(), usage, toolCalls.size(), duration);

        return new ProviderResponse(content, toolCalls, usage);

    } catch (Exception e) {
        // 失败也写审计
        long duration = System.currentTimeMillis() - start;
        audit.logFailure(sessionId, profile.getAgentName(), def.getName(),
                        profile.getModel(), duration, e.getMessage());
        throw new ProviderCallException("LLM 调用失败: " + e.getMessage(), e);
    }
}
```

### 3.3 指纹缓存实现

```java
class ProviderChatModelFactory {
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    record CacheEntry(String fingerprint, ChatModel model) {}

    ChatModel resolve(ProviderDef def) {
        String newFingerprint = def.getApiKey() + "|" + def.getBaseUrl();

        return cache.compute(def.getName(), (key, existing) -> {
            if (existing != null && existing.fingerprint().equals(newFingerprint)) {
                return existing;  // 配置未变，复用
            }
            // 配置变了或首次，重建
            ChatModel model = buildChatModel(def);
            return new CacheEntry(newFingerprint, model);
        }).model();
    }

    private ChatModel buildChatModel(ProviderDef def) {
        return switch (def.getType()) {
            case "openai" -> new OpenAiChatModel(...);
            case "azure-openai" -> new AzureOpenAiChatModel(...);
            case "qwen", "openai-compatible" -> new OpenAiChatModel(baseUrl=def.getBaseUrl(), ...);
            default -> throw new IllegalArgumentException("不支持的 provider type: " + def.getType());
        };
    }
}
```

### 3.4 ToolSchemaAdapter

```java
class ToolSchemaAdapter {
    // OryxTool → Spring AI FunctionCallback
    FunctionCallback toFunctionCallback(OryxTool tool) {
        return FunctionCallback.builder()
            .function(tool.getName(), (Function<String, String>) input -> {
                // 这个 function 不会被调用，只是占位
                throw new UnsupportedOperationException("Provider 层不执行 tool");
            })
            .description(tool.getDescription())
            .inputSchema(tool.getInputSchema())  // JSON Schema
            .build();
    }

    // 从 ChatResponse 提取 tool_calls
    List<ToolCall> extractToolCalls(ChatResponse response) {
        return response.getResults().stream()
            .filter(r -> r.getOutput().getToolCalls() != null)
            .flatMap(r -> r.getOutput().getToolCalls().stream())
            .map(tc -> new ToolCall(tc.id(), tc.name(), tc.arguments()))
            .toList();
    }
}
```

### 3.5 配置结构

#### application.yaml（全局层）

```yaml
oryxos:
  providers:
    - name: qwen-plus
      type: qwen
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      api-key: ${DASHSCOPE_API_KEY}
    - name: gpt-4o
      type: openai
      api-key: ${OPENAI_API_KEY}
    - name: my-private-llm
      type: openai-compatible
      base-url: http://internal-llm.company.com/v1
      api-key: ${INTERNAL_LLM_KEY}
```

#### ProvidersProperties 绑定

```java
@ConfigurationProperties("oryxos")
class ProvidersProperties {
    private List<ProviderDef> providers = new ArrayList<>();
    // getters/setters
}
```

#### Profile 层（Agent 配置）

```yaml
# .oryxos/profiles/weather-agent.yaml
provider:
  name: qwen-plus        # 引用全局层定义的 provider
  model: qwen-plus        # 具体模型名
  temperature: 0.7
```

### 3.6 审计表结构

```sql
CREATE TABLE llm_calls (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL,
    agent_name TEXT NOT NULL,
    provider_name TEXT NOT NULL,
    model TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'SUCCESS',  -- SUCCESS / FAILED
    prompt_tokens INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL DEFAULT 0,
    tool_call_count INTEGER NOT NULL DEFAULT 0,
    duration_ms INTEGER NOT NULL,
    error_message TEXT,                       -- 失败时记录原因
    timestamp TEXT NOT NULL
);
```

### 3.7 数据流走向

```
用户输入
  ↓
ReActLoop 构建 Prompt（messages + tools）
  ↓
ProviderService.chat(sessionId, profile, prompt)
  ↓
ProviderRegistry.find(profile.providerName) → ProviderDef
  ↓
ProviderChatModelFactory.resolve(def) → ChatModel（指纹缓存）
  ↓
buildOptions(def, profile, tools) → ChatOptions
  ↓
ToolSchemaAdapter.toFunctionCallback(tools) → List<FunctionCallback>
  ↓
chatModel.call(new Prompt(messages, options))
  ↓
ChatResponse（原始响应）
  ↓
extractContent() + extractToolCalls() + extractUsage()
  ↓
ProviderResponse(content, toolCalls, usage)
  ↓
AuditLogger.log() → llm_calls 表
  ↓
返回给 ReActLoop
```

### 3.8 异常处理策略

| 异常场景 | 处理方式 |
|----------|----------|
| Provider 不存在 | 抛 `ProviderNotFoundException`，不静默 fallback |
| LLM API 调用失败 | 写审计（status=FAILED），抛 `ProviderCallException` |
| ChatOptions 类型不匹配 | 在 buildOptions() 内 switch 处理，不会走到 default |
| Tool Schema 转换失败 | 抛 `IllegalArgumentException`，上层捕获并记录日志 |

---

## 4. 验收 harness：把验收标准变成可执行的测试

### 4.1 测试分层

| 层级 | 覆盖目标 | 工具 | 是否必须 |
|------|----------|------|----------|
| 单元测试 | 单个类的逻辑正确性 | JUnit 5 + Mockito | 必须 |
| 集成冒烟测试 | 多个类协作的端到端流程 | Spring Boot Test + Testcontainers（可选） | 必须 |
| E2E 测试 | 真实 LLM API 调用 | 手动执行 | 核心阶段不做 |

### 4.2 单元测试用例

#### SpringAiProviderServiceImplTest

| 用例名 | 输入 | 期望输出 | 验证点 |
|--------|------|----------|--------|
| chat_success_no_tools | 正常 prompt，无 tool | ProviderResponse，content 非空，toolCalls 为空 | 审计 status=SUCCESS |
| chat_success_with_tools | 正常 prompt，有 tool | ProviderResponse，toolCalls 非空 | tool_calls 是原始数据，未被执行 |
| chat_provider_not_found | profile 引用不存在的 provider | 抛 ProviderNotFoundException | 审计无记录 |
| chat_llm_call_failed | ChatModel 抛异常 | 抛 ProviderCallException | 审计 status=FAILED，error_message 非空 |
| chat_options_merged | profile 指定 model + temperature | ChatOptions 包含 profile 的值 | buildOptions() 合并逻辑正确 |

#### ToolSchemaAdapterTest

| 用例名 | 输入 | 期望输出 | 验证点 |
|--------|------|----------|--------|
| toFunctionCallback_success | OryxTool 实例 | FunctionCallback 实例 | name/description/inputSchema 一致 |
| extractToolCalls_empty | 无 tool_calls 的响应 | 空列表 | |
| extractToolCalls_multiple | 多个 tool_calls 的响应 | ToolCall 列表 | id/name/arguments 正确提取 |

#### ProviderChatModelFactoryTest

| 用例名 | 输入 | 期望输出 | 验证点 |
|--------|------|----------|--------|
| resolve_first_time | 新 ProviderDef | 构建 ChatModel 并缓存 | cache 有一条记录 |
| resolve_cached | 相同 ProviderDef | 返回缓存实例 | ChatModel 是同一个对象 |
| resolve_config_changed | apiKey 变了 | 重建 ChatModel | fingerprint 变了，实例不同 |

#### InMemoryProviderRegistryTest

| 用例名 | 输入 | 期望输出 | 验证点 |
|--------|------|----------|--------|
| save_and_find | save(def) → find(name) | 返回相同 def | |
| find_not_found | find("nonexist") | 抛 ProviderNotFoundException | |

#### ProviderBootstrapTest

| 用例名 | 输入 | 期望输出 | 验证点 |
|--------|------|----------|--------|
| load_from_yaml | ProvidersProperties 有 2 个 provider | Registry 有 2 条记录 | name 匹配 |

### 4.3 集成冒烟测试用例

#### ProviderSmokeTest

| 用例名 | 输入 | 期望输出 | 验证点 |
|--------|------|----------|--------|
| smoke_full_flow | YAML 配置 → Registry → ChatModel → 调用 → 审计 | 完整流程跑通 | 1. Registry 注册成功 2. ChatModel 构建成功 3. 审计表有记录 |

**运行命令**：

```bash
mvn -pl oryxos-provider test
mvn -pl oryxos-provider test -Dtest=ProviderSmokeTest
```

### 4.4 测试数据准备

- 单元测试：Mockito mock ChatModel、ProviderRegistry
- 集成测试：使用 `application-test.yaml` 配置内存 Provider，不依赖真实 API
- 审计表：使用 H2 内存数据库或 Testcontainers SQLite

---

## 5. 做完怎样验

### 5.1 人工确认项

| 核验项 | 通过标准 | 是否 blocking |
|--------|----------|---------------|
| Spring AI 自动执行已禁用 | 代码中无 `ChatClient` 调用，无 `Advisor` 使用 | blocking |
| chat() 返回原始 tool_calls | 单元测试验证 toolCalls 是原始数据，未被执行 | blocking |
| 显式映射无隐式降级 | provider 不存在时抛异常，不静默 fallback | blocking |
| 审计表成功/失败双记录 | 单元测试验证两种场景都写入 llm_calls | blocking |
| 配置分层清晰 | application.yaml 只有连接信息，Profile 只有使用策略 | blocking |

### 5.2 自动化验收标准

| 验收命令 | 期望结果 | 是否 blocking |
|----------|----------|---------------|
| `mvn -pl oryxos-provider test` | BUILD SUCCESS，所有测试通过 | blocking |
| `mvn -pl oryxos-provider verify` | 含 spotless:check、spotbugs、PMD，全部通过 | blocking |
| `mvn spotless:check` | 代码格式符合 google-java-format AOSP | blocking |
| 代码搜索 `ChatClient` | 无结果（grep "ChatClient" oryxos-provider/） | blocking |
| 代码搜索 `Advisor` | 无结果（grep "Advisor" oryxos-provider/） | blocking |

### 5.3 与 agents.md 原则对齐检查

| agents.md 原则 | 对齐方式 | 验证方法 |
|----------------|----------|----------|
| #1 自实现 ReAct Loop | Provider 只返回 tool_calls，不执行 | 单元测试验证 |
| #2 Spring AI 边界 | 只用 ChatModel.call()，不用 ChatClient | 代码搜索 |
| #3 同步执行 + Virtual Threads | chat() 是同步方法，无 Reactor/WebFlux | 代码审查 |
| #4 OryxTool 抽象 | ToolSchemaAdapter 把 OryxTool 转成 FunctionCallback | 单元测试验证 |
| #7 审计 Day One | llm_calls 表 Day One 就写入，成功/失败都记录 | 集成测试验证 |

### 5.4 性能/安全/可观测性验收

| 维度 | 验收点 | 通过标准 |
|------|--------|----------|
| 性能 | 指纹缓存命中时不重建 ChatModel | 单元测试验证缓存逻辑 |
| 安全 | apiKey 从环境变量注入，不明文硬编码 | 代码搜索硬编码密钥 |
| 可观测性 | 审计表记录 duration_ms、token 用量、tool_call_count | 集成测试验证字段非空 |
| 可观测性 | 结构化日志包含 sessionId、agentName、providerName | 日志输出检查 |

### 5.5 扩展性预留验证

| 扩展点 | 预留方式 | 验证方法 |
|--------|----------|----------|
| 故障降级/fallback | ProviderDef.fallbacks 字段已定义 | 代码审查 |
| 持久化 Registry | ProviderRegistry 接口已定义，InMemoryProviderRegistry 是实现 | 接口/实现分离审查 |
| 成本看板 | llm_calls 表记录 token 用量 | 表结构审查 |

---

## 变更记录

| 日期 | 变更内容 | 变更人 |
|------|----------|--------|
| 2026-10-13 | 初始版本，设计冻结 | 与用户多轮讨论对齐 |
