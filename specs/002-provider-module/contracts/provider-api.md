# Contracts: Provider API

**Feature**: 002-provider-module | **Date**: 2026-10-13

## ProviderService (oryxos-core interface)

对外唯一门面，ReAct 循环通过此接口调用 LLM。

```java
package com.oryxos.core.agent;

public interface ProviderService {
    ProviderResponse chat(String sessionId, Profile profile, Prompt prompt);
}
```

**调用方**：ReActLoop (oryxos-core)
**实现方**：SpringAiProviderServiceImpl (oryxos-provider)

### 行为契约

| 场景 | 行为 |
|------|------|
| 正常调用（无 tool） | 返回 ProviderResponse，content 非空，toolCalls 为空列表 |
| 正常调用（有 tool） | 返回 ProviderResponse，toolCalls 包含原始 tool_call 数据 |
| Provider 不存在 | 抛出 ProviderNotFoundException |
| LLM API 失败 | 抛出 ProviderCallException，审计记录 status=FAILED |
| 成功/失败 | 均写入 llm_calls 审计记录 |

## ProviderRegistry (oryxos-core interface)

Provider 注册表，按 name 查找 ProviderDef。

```java
package com.oryxos.core.agent;

public interface ProviderRegistry {
    void save(ProviderDef def);
    ProviderDef find(String name);  // 找不到 → ProviderNotFoundException
}
```

## 配置契约

### application.yaml 全局层

```yaml
oryxos:
  providers:
    - name: <string>          # 必填，唯一标识
      type: <string>          # 必填，openai|azure-openai|qwen|openai-compatible
      api-key: <env-var>      # 必填，环境变量引用
      base-url: <string>      # 可选，自定义端点
```

### Profile 层

```yaml
provider:
  name: <string>              # 引用全局层 provider name
  model: <string>             # 具体模型名
  temperature: <float>        # 0.0~2.0
```

## 审计记录契约 (llm_calls)

每次 LLM 调用写入一条记录：

| 字段 | 来源 | 说明 |
|------|------|------|
| session_id | chat() 参数 | 会话标识 |
| agent_name | profile.getName() | Agent 名称 |
| provider_name | ProviderDef.name() | Provider 名称 |
| model | profile.getModel() | 模型名 |
| status | 调用结果 | SUCCESS / FAILED |
| prompt_tokens | ChatResponse metadata | 输入 token |
| completion_tokens | ChatResponse metadata | 输出 token |
| total_tokens | ChatResponse metadata | 总 token |
| tool_call_count | toolCalls.size() | tool_call 数量 |
| duration_ms | 计时 | 调用耗时（毫秒） |
| error_message | 异常 message | 失败时记录 |
| created_at | Instant.now() | 时间戳 |
