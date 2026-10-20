# Data Model: ReAct 模块

## 变更实体

### Message（改造）

现有 `Message` 类新增 `toolCallId` 字段，用于 tool 消息关联对应的 tool_call。

| 字段 | 类型 | 说明 |
|------|------|------|
| role | String | 消息角色：user / assistant / system / tool |
| content | String | 消息内容 |
| toolCallId | String | 新增，可选。tool 消息关联 tool_call 的 id |

### ToolInvocationEntity（改造）

现有实体新增 `toolCallId` 字段。

| 字段 | 列名 | 类型 | 说明 |
|------|------|------|------|
| toolCallId | tool_call_id | VARCHAR(64) | 新增，关联 LLM 返回的 tool_call id |

### ToolInvocationRepository（改造）

新增查询方法：

```java
List<ToolInvocationEntity> findByToolCallId(String toolCallId);
```

## 迁移脚本

### V5__add_tool_call_id.sql

```sql
ALTER TABLE tool_invocations ADD COLUMN tool_call_id VARCHAR(64);
```

## 不变实体（Day 1 已定义）

以下实体本次不修改，仅列出供参考：

- **Profile**: 确认 maxIterations（默认 10）、maxHistoryTurns（默认 20）字段存在
- **ProviderResponse**: content + toolCalls + usage
- **ToolCall**: id + name + arguments（record）
- **Prompt**: messages + tools（record）
- **Session**: sessionId + messages + profileName

## 删除类型

| 类型 | 替代者 |
|------|--------|
| ToolCallRequest | ToolCall（Day 1 record） |
| LlmPort | ProviderService |
| LlmPortFactory | ProviderService |
