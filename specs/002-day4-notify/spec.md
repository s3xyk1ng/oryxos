# 功能规格：Notify 通知推送模块

**Feature Branch**: `004-day4-notify`

**创建日期**: 2026-10-22

**状态**: Draft

**输入**: 用户描述: "Day 4 — Notify（通知推送）：Agent 执行完毕后，主动把结果推送到用户能看到的地方"

---

## Clarifications

### Session 2026-10-22

- Q: Notify 模块的核心阶段是否需要逐一接入企微/飞书/钉钉专用 API？ → A: 核心阶段只做通用 webhook（HTTP POST），兼容企微/飞书/钉钉群机器人格式，不接专用 API（签名算法、AccessToken 刷新等留扩展阶段）。
- Q: 接口是否需要 getType() 方法做路由器？ → A: 不需要，接口保持中立，只有一个 send() 方法，适配器自己判断 type 是否匹配。
- Q: NotifyTarget 是否需要 name 字段？ → A: 不需要，只保留 type 和 config 两个字段。
- Q: 渠道未配置时工具是否仍然注册？ → A: 不注册。notify_channels 表为空时，notify 工具不注册到 ToolRegistry，LLM 看不到该工具。
- Q: 5xx 响应如何处理？ → A: 向上抛异常，不吞掉，不返回错误字符串。
- Q: Sandbox 校验如何实现？ → A: 核心阶段先 TODO 占位，D=9 接线。但执行顺序钉死：校验在前，发送在后。
- Q: 凭证如何管理？ → A: config 中支持 ${ENV_VAR} 占位符，运行时从环境变量解析，不明文落库。核心阶段先 TODO 占位。

---

## 用户场景与测试 *(必填)*

Notify 模块交付 Agent 的出站推送能力。以下 4 个 User Story 按依赖关系排列，每个都是可独立测试的功能切片。

推进顺序：US-1 → US-2 → US-3 → US-4

---

### User Story 1 - 通用 Webhook 适配器（接口中立 + HTTP POST）(Priority: P1)

某企业有一个飞书群机器人 webhook 地址。开发者实现一个 WebhookNotifyAdapter，任何类型的通知渠道只要走 HTTP POST，都能复用这个适配器发送消息。适配器接口完全中立——参数不叫 webhookUrl 而叫 target，不叫 text 而叫 content——将来扩展其他渠道类型时接口不变。

**为什么是这个优先级**：Webhook 适配器是 Notify 模块唯一的核心阶段实现，是所有出站推送的基础。没有它，Notify 模块无法发送任何消息。

**独立测试**：可以通过 mock HttpServer 验证适配器发出的 POST 请求 body 包含 content、URL 来自 NotifyTarget 而非硬编码、5xx 响应向上抛异常来独立验证。

**验收场景**：

1. **Given** 一个 type=webhook 的 NotifyTarget，config 中包含 url 指向 mock server，**When** 调用 adapter.send(target, "hello")，**Then** mock server 收到 HTTP POST 请求，body 为 JSON 格式 `{"msgtype":"text","text":{"content":"hello"}}`，返回 "Notification sent successfully (HTTP 200)"。
2. **Given** 一个 type=smtp 的 NotifyTarget，**When** 调用 adapter.send(target, "hello")，**Then** 返回 "unsupported type: smtp"，不发出任何 HTTP 请求。
3. **Given** 一个 type=webhook 的 NotifyTarget，config 中 url 为空，**When** 调用 adapter.send(target, "hello")，**Then** 抛出 IllegalArgumentException，不发出 HTTP 请求。
4. **Given** mock server 返回 HTTP 500，**When** 调用 adapter.send(target, "hello")，**Then** 抛出 RuntimeException（包含状态码和响应体信息），不吞掉错误。
5. **Given** 两个不同 url 的 NotifyTarget，**When** 分别调用 adapter.send()，**Then** 两次 POST 请求发往不同的 URL 地址，URL 来自 target.getConfig().get("url") 而非硬编码。

---

### User Story 2 - NotifyTools 编排（渠道查找 + 适配器路由 + 错误传递）(Priority: P1)

Agent 调用 notify 工具时，传入渠道注册名（如 "team-lark"）和消息内容。NotifyTools 从全局注册表查找渠道配置，构建 NotifyTarget，遍历所有适配器找到匹配的处理。任何一步失败都有明确的错误信号返回给 Agent，绝不静默跳过。

**为什么是这个优先级**：NotifyTools 是 Agent 与适配器之间的桥梁。没有它，Agent 无法使用通知推送能力。

**独立测试**：可以通过 mock Repository 和 mock Adapter 验证渠道不存在报错、正常发送、无匹配适配器报错、参数校验等场景来独立验证。

**验收场景**：

1. **Given** 注册表中没有名为 "nonexistent" 的渠道，**When** 执行 notify(channel="nonexistent", content="hello")，**Then** 返回 ToolResult.fail("Channel not found: nonexistent")。
2. **Given** 注册表中有 "team-lark" 渠道（type=webhook），适配器正常返回成功，**When** 执行 notify(channel="team-lark", content="hello")，**Then** 返回 ToolResult.ok("Notification sent successfully...")。
3. **Given** 渠道 type=smtp，但只有 webhook 适配器，**When** 执行 notify()，**Then** 返回 ToolResult.fail("No adapter supports channel type: smtp")。
4. **Given** content 参数为空，**When** 执行 notify()，**Then** 返回 ToolResult.fail("content parameter is required")。
5. **Given** LLM 未传 channel 参数，Profile 配置了 defaultNotifyChannel="team-lark"，**When** 执行 notify(content="hello")，**Then** 使用 "team-lark" 渠道发送。
6. **Given** LLM 未传 channel 参数，Profile 也没有 defaultNotifyChannel，**When** 执行 notify()，**Then** 返回 ToolResult.fail("No notification channel specified and no default configured.")。

---

### User Story 3 - Profile 默认渠道解析（AGENT.md frontmatter）(Priority: P2)

管理员在 AGENT.md 的 frontmatter 中配置 `default_notify_channel: team-lark`。AgentLoader 解析 frontmatter 时将该值设入 Profile.defaultNotifyChannel。LLM 调用 notify 不传 channel 参数时，自动使用默认渠道。

**为什么是这个优先级**：默认渠道简化了 LLM 的调用负担，但不影响核心发送功能。

**独立测试**：可以通过构造含/不含 default_notify_channel 的 frontmatter YAML，验证 AgentLoader.deriveProfile() 解析结果来独立验证。

**验收场景**：

1. **Given** AGENT.md frontmatter 包含 `default_notify_channel: team-lark`，**When** AgentLoader.deriveProfile() 解析，**Then** profile.getDefaultNotifyChannel() 返回 "team-lark"。
2. **Given** AGENT.md frontmatter 不包含 default_notify_channel 字段，**When** AgentLoader.deriveProfile() 解析，**Then** profile.getDefaultNotifyChannel() 返回 null，不报错。

---

### User Story 4 - 工具注册控制（渠道表为空时不注册）(Priority: P2)

系统启动时，如果 notify_channels 表为空（没有任何渠道配置），则不注册 notify 工具到 ToolRegistry。LLM 看不到该工具的 schema，就不会生成 notify 的 tool_call，从源头避免无效调用。

**为什么是这个优先级**：这是一个防御性设计，不影响有渠道配置时的正常功能。

**独立测试**：可以通过 mock Repository 返回 count=0，验证 ToolAutoRegistrar 不注册 NotifyTool 来独立验证。

**验收场景**：

1. **Given** notify_channels 表为空（count=0），**When** 系统启动执行 ToolAutoRegistrar，**Then** notify 工具不注册到 ToolRegistry，日志输出 "No notify channels configured, notify tool will not be available"。
2. **Given** notify_channels 表至少有一条记录，**When** 系统启动，**Then** notify 工具正常注册，LLM 可以看到并调用。

---

## 功能需求

### FR1: 通知渠道全局注册表
通知渠道以全局注册表方式管理（SQLite `notify_channels` 表），每个渠道有唯一注册名（name）、类型（type）、地址（url）和扩展配置（config_json）。

### FR2: Agent 只认渠道注册名
Agent 通过渠道注册名（如 "team-lark"）引用通知渠道，不接触具体 webhook 地址。地址从注册表解析。

### FR3: 适配器接口中立
NotifyChannelAdapter 接口只有一个业务方法 `String send(NotifyTarget target, String content)`，参数命名不绑定任何具体渠道类型。接口不包含 getType() 方法。

### FR4: 通用 Webhook 适配器
核心阶段实现 WebhookNotifyAdapter，用 HTTP POST 发送 JSON payload，格式为 `{"msgtype":"text","text":{"content":"..."}}`，兼容企微/飞书/钉钉群机器人。JSON 构建使用 Jackson ObjectMapper。

### FR5: 适配器从 config 读取各自需要的 key
不同适配器从 NotifyTarget.config 中读取各自需要的配置项：webhook 读 url，smtp 读 host/port/from/to 等（smtp 扩展阶段实现）。

### FR6: 凭证走环境变量占位符
config 中的值支持 `${ENV_VAR}` 占位符语法，运行时从环境变量解析，不明文落库。核心阶段先 TODO 占位。

### FR7: Profile 默认通知渠道
Profile 新增 defaultNotifyChannel 字段，从 AGENT.md frontmatter 的 `default_notify_channel` 键解析。LLM 不传 channel 参数时使用默认值。

### FR8: Sandbox 白名单校验占位
发送前必须过 Sandbox 域名白名单校验。核心阶段先 TODO 占位，D=9 接线。执行顺序钉死：校验在前，发送在后。

### FR9: 不能静默失败
每一层错误都必须有明确信号向上传递：5xx 抛异常、渠道不存在返回 ToolResult.fail、无配置返回 ToolResult.fail。

### FR10: 校验在前，发送在后
Sandbox 校验必须在 HTTP 请求之前执行。用 InOrder 测试钉死顺序。Sandbox 不通过时 HTTP 请求不发出。

### FR11: 渠道表为空时不注册工具
notify_channels 表为空时，notify 工具不注册到 ToolRegistry，LLM 看不到该工具。

---

## 明确不做（边界）

1. **不逐一接入企微/飞书/钉钉专用 API**：签名算法、AccessToken 刷新等留扩展阶段。核心阶段只做通用 webhook。
2. **不做渠道 CRUD 的 Web API**：`/api/v1/notify-channels` 端点留扩展阶段。
3. **不新增审计逻辑**：走 ToolExecutor 现有审计路径，写入 tool_invocations。
4. **不实现 Sandbox 本身**：D=9 的任务，这里只留调用占位（TODO 注释）。
5. **不实现 EmailNotifyAdapter（SMTP）**：扩展阶段实现。
6. **不在 AGENT.md frontmatter 中添加 notify_channels 字段**：渠道通过全局注册表管理，frontmatter 只放 default_notify_channel。

---

## 验收标准

### 自动化验收（mvn test 全绿即通过）

| 测试类 | 关键回归测试 | 守点 |
|--------|-------------|------|
| WebhookNotifyAdapterTest | send_success | POST 发出，payload 含 content |
| WebhookNotifyAdapterTest | send_typeMismatch | 返回 "unsupported type: xxx" |
| WebhookNotifyAdapterTest | send_urlMissing | 抛 IllegalArgumentException |
| WebhookNotifyAdapterTest | send_http5xx | 抛 RuntimeException，不吞掉 |
| WebhookNotifyAdapterTest | send_urlComesFromTarget | URL 来自 target，不硬编码 |
| WebhookNotifyAdapterTest | send_postBodyContainsContent | body JSON 含 content 字段 |
| NotifyToolsTest | execute_channelNotFound | ToolResult.fail("Channel not found") |
| NotifyToolsTest | execute_success | 查到渠道 → 构建 target → 调适配器 → 成功 |
| NotifyToolsTest | execute_noMatchingAdapter | ToolResult.fail("No adapter supports...") |
| NotifyToolsTest | execute_contentBlank | ToolResult.fail("content parameter is required") |
| NotifyToolsTest | execute_defaultChannelFallback | LLM 不传 channel 时用 Profile 默认值 |
| AgentLoaderNotifyTest | deriveProfile_withDefaultNotifyChannel | frontmatter 解析正确 |
| AgentLoaderNotifyTest | deriveProfile_withoutDefaultNotifyChannel | 不报错，默认 null |
| InOrder 测试 | sandboxCheckBeforeSend | Sandbox 校验在 HTTP 发送之前 |
| InOrder 测试 | sandboxRejectBlocksSend | Sandbox 不通过时 HTTP 不发出 |

### 人工验收（见课件"五、做完怎么验"）

1. 真实 webhook 端到端：在企微/飞书群创建机器人 → 注册渠道 → Agent 调 notify → 群里收到消息
2. 接口中立性验证：写出 WechatWorkAdapter / DingTalkAdapter 骨架，只实现接口即可
3. 渠道未配置报错：不注册任何渠道 → Agent 调 notify → 返回明确错误

---

## 依赖与假设

### 前序天交付物

- **Day 1**：OryxTool / ToolResult 接口、ProviderService、Profile / ProfileRegistry
- **Day 2**：AgentService、ReActLoop、ToolExecutor、ProfileContext、ContextLoader
- **Day 3**：OryxOsCli、ChatCommand、CLI 轻重命令路由

### 已存在的骨架代码（Day 1 实现，需按新设计重构）

- NotifyChannelAdapter 接口
- WebhookNotifyAdapter 实现
- NotifyTools 工具类

### 已存在的存储层

- NotifyChannelEntity（JPA 实体）
- NotifyChannelRepository（Spring Data JPA）
- V2__create_notify_channels.sql（建表脚本）

### 外部依赖

- Jackson ObjectMapper（Spring Boot 已有）
- JDK HttpClient（JDK 21 内置）
- JUnit 5 + Mockito（测试）
- JDK 内置 HttpServer（com.sun.net.httpserver，mock webhook 服务端，零依赖）

### 假设

- notify_channels 表已由 Flyway 迁移脚本创建
- ToolAutoRegistrar 机制已就绪（收集所有 OryxTool Bean 自动注册）
- ProfileContext 已实现 ThreadLocal 传递当前 Profile
