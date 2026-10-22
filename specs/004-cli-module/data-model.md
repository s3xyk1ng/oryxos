# Data Model: CLI Module

**Feature**: 004-cli-module  
**Date**: 2026-10-20  
**Status**: Complete

---

## Entity 1: Command（命令）

**职责**: 描述 CLI 中的一个命令，包括名称、类型、描述、子命令。

| 属性 | 类型 | 说明 | 约束 |
|------|------|------|------|
| name | String | 命令名 | 非空，如 "chat"、"profile" |
| type | Enum | 命令类型 | HEAVY 或 LIGHT |
| description | String | 命令描述 | 非空 |
| subcommands | List<Command> | 子命令列表 | 可为空（叶子命令无子命令） |

**关系**:
- `ProfileCommand` 有 4 个子命令：`ProfileListCommand`、`ProfileCreateCommand`、`ProfileShowCommand`、`ProfileDeleteCommand`
- `ProviderCommand` 有 1 个子命令：`ProviderListCommand`
- `ToolCommand` 有 1 个子命令：`ToolListCommand`

**存储**: 不持久化，运行时由 Picocli 注解声明。

---

## Entity 2: Profile（Agent 配置）

**职责**: 描述一个 Agent 的配置，从 AGENT.md 的 frontmatter 解析。

| 属性 | 类型 | 说明 | 约束 |
|------|------|------|------|
| name | String | Agent 名称 | 非空，唯一，如 "weather" |
| description | String | Agent 描述 | 非空 |
| provider | String | 绑定的 LLM Provider 名称 | 非空，如 "deepseek" |
| model | String | 模型名称 | 非空，如 "deepseek-chat" |
| max_iterations | Integer | 最大 ReAct 循环次数 | 默认 10，范围 [1, 50] |
| max_history_turns | Integer | 最大历史轮数 | 默认 5，范围 [1, 20] |

**关系**:
- 一个 Profile 对应一个 AGENT.md 文件（`.oryxos/agents/<name>/AGENT.md`）
- 一个 Profile 关联一个 Provider（通过 provider 名称）
- 一个 Profile 可以创建多个 Session

**存储**: 文件系统（AGENT.md），不持久化到数据库。

**验证规则**:
- `name` 不能包含特殊字符（只允许 `[a-zA-Z0-9_-]`）
- `provider` 必须是已配置的 Provider 名称
- `max_iterations` 和 `max_history_turns` 必须是正整数

---

## Entity 3: Session（会话）

**职责**: 描述一次用户与 Agent 的对话会话，包含对话历史。

| 属性 | 类型 | 说明 | 约束 |
|------|------|------|------|
| sessionId | String | 会话唯一标识 | SHA-256(channel + ":" + userId + ":" + profileName) 前 64 字符 |
| profileName | String | 关联的 Agent 名称 | 非空 |
| channel | String | 渠道 | 非空，如 "cli"、"web" |
| userId | String | 用户标识 | 非空，如 "cli-user" |
| messages | List<Message> | 对话历史 | 可为空（新会话无消息） |
| status | String | 状态 | "active" 或 "archived" |
| createdAt | LocalDateTime | 创建时间 | 自动生成 |
| updatedAt | LocalDateTime | 更新时间 | 自动更新 |

**关系**:
- 一个 Session 关联一个 Profile（通过 profileName）
- 一个 Session 包含多条 Message
- SessionManager 内存版用 ConcurrentHashMap 存储，核心阶段不持久化

**存储**: 
- **核心阶段**：ConcurrentHashMap（内存），重启丢失
- **扩展阶段**：SQLite sessions 表（已有 V1 迁移脚本）

**验证规则**:
- `sessionId` 必须唯一（三元组决定）
- `status` 只能是 "active" 或 "archived"
- `messages` 中的 Message 按时间顺序排列

---

## Entity 4: Message（消息）

**职责**: 描述对话中的一条消息，包括角色、内容、时间戳。

| 属性 | 类型 | 说明 | 约束 |
|------|------|------|------|
| role | String | 消息角色 | "user" 或 "assistant" 或 "system" 或 "tool" |
| content | String | 消息内容 | 非空 |
| timestamp | LocalDateTime | 发送时间 | 自动生成 |
| toolCalls | List<ToolCall> | 工具调用（仅 assistant 消息） | 可为空 |
| toolResult | ToolResult | 工具结果（仅 tool 消息） | 可为空 |

**关系**:
- 一个 Session 包含多条 Message
- 一条 Message 可以关联多个 ToolCall（assistant 消息）
- 一条 Message 可以关联一个 ToolResult（tool 消息）

**存储**: 作为 Session 的 messages_json 字段（JSON 序列化），核心阶段不单独持久化。

**验证规则**:
- `role` 必须是 "user"、"assistant"、"system"、"tool" 之一
- `content` 不能为空
- `timestamp` 必须早于当前时间

---

## Entity 5: SessionEntity（数据库实体）

**职责**: SQLite sessions 表的 JPA 实体映射（已有，本次不修改）。

| 属性 | 列名 | 类型 | 说明 |
|------|------|------|------|
| id | id | Long | 主键，自增 |
| sessionId | session_id | String | 会话唯一标识 |
| profileName | profile_name | String | 关联的 Agent 名称 |
| channel | channel | String | 渠道 |
| userId | user_id | String | 用户标识 |
| messagesJson | messages_json | String | 对话历史（JSON） |
| status | status | String | 状态 |
| createdAt | created_at | LocalDateTime | 创建时间 |
| updatedAt | updated_at | LocalDateTime | 更新时间 |

**关系**:
- 对应 `Session` 领域模型（通过 SessionRepository 转换）
- 表已创建（V1__init_schema.sql）

**存储**: SQLite sessions 表。

---

## State Transitions

### Session 状态机

```
[新建] --getOrCreateSession()--> [active]
[active] --archive()--> [archived]
[archived] --reactivate()--> [active]
```

**核心阶段**：只实现 `active` 状态，不实现 `archive` 和 `reactivate`。

---

## Identity & Uniqueness

### Session ID 生成规则

```java
String sessionId = SHA-256(channel + ":" + userId + ":" + profileName).substring(0, 64);
```

**示例**:
- channel="cli", userId="cli-user", profileName="weather"
- 输入: "cli:cli-user:weather"
- SHA-256: "a1b2c3d4e5f6..." (64 字符)

**唯一性保证**: 三元组 (channel, userId, profileName) 唯一确定一个 Session。

---

## Data Volume & Scale

| 实体 | 预估规模 | 说明 |
|------|----------|------|
| Profile | 10~100 | 单工作区 Agent 数量 |
| Session | 100~10000 | 核心阶段内存版，重启丢失 |
| Message | 10~1000 per Session | 单会话对话轮数 |

**性能要求**:
- Profile 文件操作：< 50ms
- Session 内存读写：< 1ms
- 轻命令启动：< 100ms

---

## Summary

| 实体 | 存储 | 持久化 | 说明 |
|------|------|--------|------|
| Command | 运行时 | 否 | Picocli 注解声明 |
| Profile | 文件系统 | 是（AGNET.md） | 轻命令直接读写 |
| Session | 内存 | 否（核心阶段） | ConcurrentHashMap |
| Message | 内存 | 否（核心阶段） | 作为 Session 的一部分 |
| SessionEntity | SQLite | 是（已有） | 核心阶段不使用 |

**核心阶段只使用内存版 SessionManager，不写 SQLite。**
