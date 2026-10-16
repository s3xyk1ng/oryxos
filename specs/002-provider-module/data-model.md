# Data Model: Provider 模块

**Feature**: 002-provider-module | **Date**: 2026-10-13

## 新增数据模型（oryxos-core）

### ProviderDef (record)

Provider 连接定义，由全局配置注册。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| name | String | 非空，唯一标识 | Provider 名称（如 "qwen-plus"） |
| type | String | 非空，枚举值 | openai / azure-openai / qwen / openai-compatible |
| apiKey | String | 非空 | 凭证，走环境变量注入 |
| baseUrl | String | 可空 | 自定义端点（openai 默认不填，兼容协议必填） |
| fallbacks | List\<String\> | 可空，预留 | 故障降级列表（Day 1 不使用） |

### ProviderResponse (record)

LLM 调用返回结果。

| 字段 | 类型 | 说明 |
|------|------|------|
| content | String | 文本内容（可为空） |
| toolCalls | List\<ToolCall\> | 原始 tool_call 列表（可为空列表） |
| usage | Usage | Token 用量 |

辅助方法：`hasToolCalls()` → `toolCalls != null && !toolCalls.isEmpty()`

### ToolCall (record)

原始 tool_call 数据。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | String | tool_call 唯一标识 |
| name | String | 工具名称 |
| arguments | String | JSON 字符串（原始未解析） |

### Usage (record)

Token 用量统计。

| 字段 | 类型 | 说明 |
|------|------|------|
| promptTokens | long | 输入 token 数 |
| completionTokens | long | 输出 token 数 |
| totalTokens | long | 总 token 数 |

### Prompt (record)

LLM 调用请求。

| 字段 | 类型 | 说明 |
|------|------|------|
| messages | List\<Message\> | 消息列表（已有 Message 类） |
| tools | List\<OryxTool\> | 可用工具列表（可为空列表） |

### ProviderNotFoundException (RuntimeException)

Provider 注册表中找不到指定 name 时抛出。

### ProviderCallException (RuntimeException)

LLM API 调用失败时抛出，包装原始异常。

## 修改数据模型

### Profile (oryxos-core, 已有)

新增字段：

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| model | String | null | 具体模型名（如 "qwen-plus"） |
| temperature | Double | null | 生成温度（0.0~2.0） |

现有 `provider` 字段语义不变——引用全局配置中的 provider name。

### LlmCallEntity (oryxos-storage, 已有)

新增字段：

| 字段 | 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|------|
| agentName | agent_name | TEXT | NOT NULL | Agent 名称 |
| status | status | TEXT | NOT NULL DEFAULT 'SUCCESS' | SUCCESS / FAILED |
| toolCallCount | tool_call_count | INTEGER | NOT NULL DEFAULT 0 | tool_call 数量 |
| errorMessage | error_message | TEXT | 可空 | 失败时的错误信息 |

现有字段保持不变：id, sessionId, provider, model, promptTokens, completionTokens, totalTokens, durationMs, createdAt。

## 数据库表变更（手工建表脚本）

不依赖 Flyway（项目暂无迁移脚本），提供手工建表 SQL 参考：

```sql
-- llm_calls 表扩展（在现有表基础上新增列）
ALTER TABLE llm_calls ADD COLUMN agent_name TEXT NOT NULL DEFAULT '';
ALTER TABLE llm_calls ADD COLUMN status TEXT NOT NULL DEFAULT 'SUCCESS';
ALTER TABLE llm_calls ADD COLUMN tool_call_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE llm_calls ADD COLUMN error_message TEXT;
```

## 实体关系

```
ProviderDef (内存) ──1:N──> LlmCallEntity (SQLite)
    name                     provider_name
    (ProviderRegistry)       (审计记录)

Profile (内存) ──引用──> ProviderDef
    provider  ──────────>   name
    model / temperature     (buildOptions 合并)
```
