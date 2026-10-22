# Data Model: Notify 通知推送模块

## 核心数据结构

### NotifyTarget（新建）

不可变 record，作为适配器 send() 的入参。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| type | String | 非空 | 渠道类型，决定路由到哪个适配器（webhook/smtp/wecom/feishu/dingtalk） |
| config | Map<String, String> | 非空，可为空 Map | 渠道特有配置，不同 type 解读不同 key |

**config key 约定**：

| type | config 中的 key |
|------|----------------|
| webhook | `url` |
| smtp（扩展阶段） | `host`, `port`, `from`, `to`, `subject`, `username`, `password`, `encryption` |
| wecom/feishu/dingtalk（扩展阶段） | `url`, `secret` |

### NotifyChannelEntity（已存在，不修改）

JPA 实体，映射 SQLite `notify_channels` 表。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Long | PK, AUTO_INCREMENT | 主键 |
| name | String(64) | NOT NULL, UNIQUE | 渠道注册名 |
| type | String(32) | NOT NULL | 渠道类型 |
| url | String(512) | NOT NULL | webhook URL |
| configJson | String(TEXT) | nullable | 扩展配置 JSON |
| description | String(256) | nullable | 渠道描述 |
| createdAt | Instant | NOT NULL | 创建时间 |

### Profile（修改：新增字段）

| 新增字段 | 类型 | 默认值 | 说明 |
|----------|------|--------|------|
| defaultNotifyChannel | String | null | 默认通知渠道注册名 |

对应 AGENT.md frontmatter key: `default_notify_channel`

## 数据关系

```
Profile.defaultNotifyChannel ──引用──→ notify_channels.name
                                            │
NotifyTools.findByName(name) ──查询──→ NotifyChannelEntity
                                            │
NotifyTools.buildTarget(entity) ──转换──→ NotifyTarget(type, config)
                                            │
NotifyChannelAdapter.send(target, content) ←──使用─── NotifyTarget
```

## 验证规则

| 规则 | 适用对象 | 说明 |
|------|---------|------|
| type 非空 | NotifyTarget | 构造时校验 |
| config 非空 | NotifyTarget | 允许空 Map，不允许 null |
| name 唯一 | NotifyChannelEntity | 数据库 UNIQUE 约束 |
| url 非空 | WebhookNotifyAdapter | send() 内校验，空则抛 IllegalArgumentException |
| content 非空 | NotifyTools | execute() 入口校验，空则返回 ToolResult.fail |
