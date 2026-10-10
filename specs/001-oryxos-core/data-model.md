# 数据模型

本文档定义 OryxOS 核心阶段的数据模型，包括 SQLite 关系型数据和文件系统数据。

---

## 实体关系总览

```
┌──────────┐     ┌───────────────────┐     ┌──────────────┐
│ sessions │────<│ tool_invocations  │     │ llm_calls    │
│          │     └───────────────────┘     │              │
│          │────<│                         │              │
└──────────┘                               └──────────────┘
      │
      │ profile_name
      ▼
┌──────────────────┐     ┌──────────────────┐
│ scheduled_tasks  │────<│ task_executions  │
└──────────────────┘     └──────────────────┘

文件系统数据：
.oryxos/agents/<name>/AGENT.md  →  派生 Profile
.oryxos/memory/MEMORY.md        →  长期记忆
.oryxos/skills/<name>/SKILL.md  →  公共 Skill
```

---

## 关系型数据（SQLite）

### sessions — 会话

| 字段             | 类型        | 约束          | 说明                                      |
|------------------|-------------|---------------|-------------------------------------------|
| `session_id`     | VARCHAR(64) | PRIMARY KEY   | Channel + user + profile 联合生成          |
| `profile_name`   | VARCHAR(64) | NOT NULL      | 关联的 Agent/Profile 名称                  |
| `channel`        | VARCHAR(32) | NOT NULL      | 接入渠道（`cli`/`http`/`scheduler`）       |
| `user_id`        | VARCHAR(64) | NOT NULL      | 用户标识（CLI 交互式输入 / IM 平台账号）    |
| `messages_json`  | TEXT        | NOT NULL      | JSON 序列化的对话历史（Message 数组）       |
| `status`         | VARCHAR(16) | NOT NULL      | `active` / `archived`                      |
| `created_at`     | TIMESTAMP   | NOT NULL      | 创建时间                                   |
| `last_active_at` | TIMESTAMP   | NOT NULL      | 最后活跃时间                               |
| `archived_at`    | TIMESTAMP   | NULLABLE      | 归档时间                                   |

**验证规则**：
- `session_id` 生成公式：`sha256(channel + ":" + user_id + ":" + profile_name)` 取前 64 字符
- `status` 仅允许 `active` 和 `archived`
- `messages_json` 为合法 JSON 数组，每个元素包含 `role`（user/assistant/tool/system）、`content`、`timestamp`

**状态转换**：
```
active ──(DELETE /api/v1/sessions/{id} 或超时)──> archived
```

---

### tool_invocations — 工具调用审计

| 字段            | 类型         | 约束          | 说明                          |
|-----------------|--------------|---------------|-------------------------------|
| `id`            | BIGINT       | PRIMARY KEY   | 自增主键                      |
| `session_id`    | VARCHAR(64)  | NOT NULL      | 关联 Session（外键）          |
| `tool_name`     | VARCHAR(64)  | NOT NULL      | 工具名称                      |
| `input_json`    | TEXT         | NOT NULL      | 调用参数（JSON）              |
| `result_json`   | TEXT         | NOT NULL      | 执行结果（JSON）              |
| `success`       | BOOLEAN      | NOT NULL      | 是否成功                      |
| `error_message` | TEXT         | NULLABLE      | 错误信息                      |
| `duration_ms`   | BIGINT       | NOT NULL      | 执行耗时（毫秒）              |
| `created_at`    | TIMESTAMP    | NOT NULL      | 调用时间                      |

**验证规则**：
- `input_json` 和 `result_json` 必须为合法 JSON
- `duration_ms` ≥ 0
- `success=false` 时 `error_message` 不应为空

---

### llm_calls — LLM 调用审计

| 字段                 | 类型        | 约束          | 说明                    |
|----------------------|-------------|---------------|-------------------------|
| `id`                 | BIGINT      | PRIMARY KEY   | 自增主键                |
| `session_id`         | VARCHAR(64) | NOT NULL      | 关联 Session（外键）    |
| `provider`           | VARCHAR(32) | NOT NULL      | Provider 名称           |
| `model`              | VARCHAR(64) | NOT NULL      | 模型名                  |
| `prompt_tokens`      | INT         | NOT NULL      | 输入 token 数           |
| `completion_tokens`  | INT         | NOT NULL      | 输出 token 数           |
| `total_tokens`       | INT         | NOT NULL      | 总 token 数             |
| `duration_ms`        | BIGINT      | NOT NULL      | 调用耗时（毫秒）        |
| `created_at`         | TIMESTAMP   | NOT NULL      | 调用时间                |

**验证规则**：
- `prompt_tokens` ≥ 0, `completion_tokens` ≥ 0
- `total_tokens` = `prompt_tokens` + `completion_tokens`

---

### scheduled_tasks — 定时任务登记

| 字段           | 类型         | 约束          | 说明                                    |
|----------------|--------------|---------------|-----------------------------------------|
| `schedule_id`  | VARCHAR(64)  | PRIMARY KEY   | 全局运行态主键（稳定 ID）                |
| `profile_name` | VARCHAR(64)  | NOT NULL      | 归属 Agent/Profile                      |
| `schedule_key` | VARCHAR(64)  | NOT NULL      | Agent 内配置键，同 Profile 内唯一        |
| `display_name` | VARCHAR(128) | NULLABLE      | 展示名称                                |
| `cron`         | VARCHAR(64)  | NOT NULL      | cron 表达式                             |
| `zone`         | VARCHAR(32)  | NOT NULL      | 时区（默认 `Asia/Shanghai`）             |
| `message`      | TEXT         | NOT NULL      | 到点发给 Agent 的消息                    |
| `enabled`      | BOOLEAN      | NOT NULL      | 是否启用（默认 true）                    |
| `retired`      | BOOLEAN      | NOT NULL      | 是否退役（配置删除/改 key 后标记）       |
| `next_run_at`  | TIMESTAMP    | NULLABLE      | 下次触发时刻                             |
| `last_run_at`  | TIMESTAMP    | NULLABLE      | 上次触发时刻                             |
| `last_status`  | VARCHAR(16)  | NULLABLE      | 上次结果 `success` / `failed`            |
| `run_count`    | INT          | NOT NULL      | 累计触发次数（默认 0）                   |
| `updated_at`   | TIMESTAMP    | NOT NULL      | 状态更新时间                             |

**验证规则**：
- `(profile_name, schedule_key)` 组合唯一
- `schedule_id` 由 `reconcile` 机制生成，对同一 `(profile_name, schedule_key)` 保持稳定
- `cron` 必须为合法 cron 表达式
- `retired=true` 的任务不参与调度

---

### task_executions — 定时任务执行历史

| 字段            | 类型        | 约束          | 说明                          |
|-----------------|-------------|---------------|-------------------------------|
| `id`            | BIGINT      | PRIMARY KEY   | 自增主键                      |
| `schedule_id`   | VARCHAR(64) | NOT NULL      | 关联 scheduled_tasks          |
| `session_id`    | VARCHAR(64) | NULLABLE      | 本次触发的 Session            |
| `started_at`    | TIMESTAMP   | NOT NULL      | 开始时间                      |
| `success`       | BOOLEAN     | NOT NULL      | 是否成功                      |
| `error_message` | TEXT        | NULLABLE      | 失败信息                      |
| `duration_ms`   | BIGINT      | NOT NULL      | 执行耗时（毫秒）              |

---

### notify_channels — 通知渠道注册表

| 字段          | 类型         | 约束          | 说明                          |
|---------------|--------------|---------------|-------------------------------|
| `id`          | BIGINT       | PRIMARY KEY   | 自增主键                      |
| `name`        | VARCHAR(64)  | UNIQUE        | 全局注册名                    |
| `type`        | VARCHAR(32)  | NOT NULL      | 渠道类型（`webhook`/`email`） |
| `url`         | TEXT         | NULLABLE      | Webhook URL                   |
| `config_json` | TEXT         | NULLABLE      | 类型相关配置（JSON）          |
| `description` | TEXT         | NULLABLE      | 描述                          |
| `created_at`  | TIMESTAMP    | NOT NULL      | 创建时间                      |

---

## 文件系统数据

### Agent 目录（`.oryxos/agents/<name>/`）

| 文件/目录         | 说明                                              |
|-------------------|---------------------------------------------------|
| `AGENT.md`        | frontmatter（运行配置）+ 正文（任务指令）          |
| `skills/<name>`   | 指向 `.oryxos/skills/<name>/` 的相对软连接        |
| `scripts/`        | 可选脚本目录                                      |
| `REFERENCE.md`    | 可选参考文档                                      |

### MEMORY.md（`.oryxos/memory/MEMORY.md`）

```markdown
## 核心记忆

（核心记忆区：永远保留，不截断，不参与检索）

## 归档记忆

（归档记忆区：可截断，关键词检索作用于此区域）

### 2026-10-09
- 用户偏好：项目用 Spring Boot，部署在 K8s 上
```

### mcp_servers.yaml（`.oryxos/mcp_servers.yaml`）

```yaml
servers:
  - name: github-mcp
    transport: stdio          # stdio | sse
    command: npx -y @modelcontextprotocol/server-github
    env:
      GITHUB_TOKEN: "${GITHUB_TOKEN}"
  - name: remote-mcp
    transport: sse
    url: http://localhost:3001/sse
```

### Bootstrap 文件

| 文件        | 说明                     | 加载方      |
|-------------|--------------------------|-------------|
| `AGENTS.md` | 项目级 Agent 行为说明    | ContextLoader |
| `SOUL.md`   | 默认 Agent 人格定义      | ContextLoader |
| `USER.md`   | 用户偏好                 | ContextLoader |

---

## Flyway 迁移目录

```
oryxos-storage/src/main/resources/db/migration/
├── sqlite/
│   ├── V1__create_sessions.sql
│   ├── V2__create_tool_invocations.sql
│   ├── V3__create_llm_calls.sql
│   ├── V4__create_scheduled_tasks.sql
│   ├── V5__create_task_executions.sql
│   └── V6__create_notify_channels.sql
└── postgresql/          # 扩展阶段
```
