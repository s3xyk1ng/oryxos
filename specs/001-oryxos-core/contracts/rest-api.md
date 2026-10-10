# REST API 契约

本文档定义 OryxOS 核心阶段的 REST API 端点契约。所有端点前缀 `/api/v1`。

---

## 通用约定

### 响应信封

所有响应使用统一信封格式：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "timestamp": "2026-10-09T10:00:00Z"
}
```

错误响应：

```json
{
  "code": 404,
  "message": "Agent not found: weather-agent",
  "data": null,
  "timestamp": "2026-10-09T10:00:00Z"
}
```

### 错误码

| HTTP 状态码 | 含义                     |
|-------------|--------------------------|
| 200         | 成功                     |
| 201         | 创建成功                 |
| 400         | 参数错误                 |
| 404         | 资源不存在               |
| 409         | 冲突（如 schedule key 重复）|
| 500         | 内部错误                 |
| 503         | Provider 故障            |
| 504         | Agent 调用超时（60s）    |

### 通用约束

- 单条消息最大 32KB
- Session 历史返回最多最近 100 条消息
- Agent 调用最长 60 秒超时
- 核心阶段无认证（假设内网）

---

## 会话管理（4 个端点）

### 创建会话

```
POST /api/v1/sessions
```

**请求体**：
```json
{
  "profile_name": "weather-agent",
  "channel": "http",
  "user_id": "user-001"
}
```

**响应**（201）：
```json
{
  "code": 201,
  "message": "success",
  "data": {
    "session_id": "a1b2c3d4...",
    "profile_name": "weather-agent",
    "channel": "http",
    "user_id": "user-001",
    "status": "active",
    "created_at": "2026-10-09T10:00:00Z"
  }
}
```

---

### 发送消息

```
POST /api/v1/sessions/{session_id}/messages
```

**请求体**：
```json
{
  "content": "查一下北京天气"
}
```

**响应**（200）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "session_id": "a1b2c3d4...",
    "response": "北京今天晴，气温 15-25°C，建议穿薄外套...",
    "tool_calls": [
      {
        "tool": "http_get",
        "input": {"url": "https://api.weather.com/..."},
        "success": true,
        "duration_ms": 320
      }
    ],
    "iterations": 2
  }
}
```

---

### 查询会话历史

```
GET /api/v1/sessions/{session_id}
```

**响应**（200）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "session_id": "a1b2c3d4...",
    "profile_name": "weather-agent",
    "channel": "http",
    "user_id": "user-001",
    "status": "active",
    "messages": [
      {"role": "user", "content": "查一下北京天气", "timestamp": "..."},
      {"role": "assistant", "content": "...", "timestamp": "..."}
    ],
    "created_at": "2026-10-09T10:00:00Z",
    "last_active_at": "2026-10-09T10:05:00Z"
  }
}
```

---

### 归档会话

```
DELETE /api/v1/sessions/{session_id}
```

**响应**（200）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "session_id": "a1b2c3d4...",
    "status": "archived",
    "archived_at": "2026-10-09T11:00:00Z"
  }
}
```

---

## Agent 调用（1 个端点）

### 无状态调用

```
POST /api/v1/agents/{profile_name}/invoke
```

**请求体**：
```json
{
  "message": "查一下北京天气",
  "user_id": "user-001"
}
```

**响应**（200）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "response": "北京今天晴，气温 15-25°C...",
    "session_id": "a1b2c3d4...",
    "iterations": 2
  }
}
```

---

## 信息查询（3 个端点）

### 列出 Profile

```
GET /api/v1/profiles
```

**响应**（200）：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "name": "weather-agent",
      "description": "天气查询助手",
      "provider": {"name": "deepseek", "model": "deepseek-chat"},
      "tools": ["http_get", "http_post", "notify"],
      "channels": ["cli"]
    }
  ]
}
```

---

### 查询长期记忆

```
GET /api/v1/memory?profile_name=weather-agent
```

**响应**（200）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": "## 核心记忆\n...\n## 归档记忆\n...",
    "size_bytes": 1024
  }
}
```

---

### 列出可用工具

```
GET /api/v1/tools
```

**响应**（200）：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "name": "read_file",
      "description": "读取文件内容",
      "source": "builtin",
      "input_schema": { ... }
    },
    {
      "name": "github_search",
      "description": "搜索 GitHub 仓库",
      "source": "mcp",
      "input_schema": { ... }
    }
  ]
}
```

---

## 系统状态（2 个端点）

### 健康检查

```
GET /api/v1/health
```

**响应**（200）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "UP",
    "components": {
      "database": "UP",
      "providers": {"deepseek": "UP", "kimi": "UP"}
    }
  }
}
```

---

### 运行信息

```
GET /api/v1/info
```

**响应**（200）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "version": "1.0.0",
    "jdk_version": "21",
    "uptime_seconds": 3600,
    "active_sessions": 5,
    "registered_profiles": 3,
    "registered_tools": 12,
    "providers": ["deepseek", "kimi"]
  }
}
```

---

## 定时任务管理（v2 端点）

### 列出定时任务

```
GET /api/v2/schedules
```

**响应**（200）：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "schedule_id": "sched-001",
      "profile_name": "weather-agent",
      "schedule_key": "morning-weather",
      "display_name": "每日天气",
      "cron": "0 8 * * *",
      "zone": "Asia/Shanghai",
      "enabled": true,
      "next_run_at": "2026-10-10T08:00:00+08:00",
      "last_run_at": "2026-10-09T08:00:00+08:00",
      "last_status": "success",
      "run_count": 15
    }
  ]
}
```

---

### 查询执行历史

```
GET /api/v2/schedules/{schedule_id}/executions
```

**响应**（200）：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "schedule_id": "sched-001",
      "session_id": "a1b2c3d4...",
      "started_at": "2026-10-09T08:00:00+08:00",
      "success": true,
      "duration_ms": 5200
    }
  ]
}
```

---

### 立即执行

```
POST /api/v2/schedules/{schedule_id}/run
```

**响应**（200）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "schedule_id": "sched-001",
    "session_id": "e5f6g7h8...",
    "status": "triggered"
  }
}
```

---

### 启用/停用

```
PUT /api/v2/schedules/{schedule_id}
```

**请求体**：
```json
{
  "enabled": false
}
```

**响应**（200）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "schedule_id": "sched-001",
    "enabled": false,
    "updated_at": "2026-10-09T12:00:00Z"
  }
}
```

---

### 按 Agent 精确定位执行

```
POST /api/v2/agents/{profile_name}/schedules/{key}/run
```

**响应**：同 `POST /api/v2/schedules/{schedule_id}/run`。当多个 Agent 使用同一 key 时返回 HTTP 409。
