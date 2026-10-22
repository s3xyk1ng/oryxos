# Notify 模块设计文档

**模块名称**：Notify（通知推送）  
**文档版本**：v1.0  
**创建日期**：2026-10-22  
**对应课件**：Day 4

---

## 第一段：模块是什么，干嘛用的

### 模块定位

Notify 是 OryxOS 的**消息出站之门**——Agent 执行完毕后，通过它将结果主动推送到用户能看到的地方（企业 IM 群、邮件等）。它解决的是"结果怎样主动推送出去"的问题。

### 解决什么问题

1. **出站推送**：Agent 跑完后，主动把结果送到异步可达的地方（企微/飞书/钉钉群、邮件）
2. **渠道解耦**：Agent 只认渠道注册名（如 `team-lark`），不接触具体 webhook 地址
3. **统一接口**：不同通知渠道（webhook/SMTP/专用 API）实现同一接口，上层零感知
4. **安全可控**：发送前过 Sandbox 域名白名单，凭证走环境变量不明文落库

### 上下游关系

```
Agent（ReAct 循环）
  │
  └─ 调用 notify Tool
       │
       NotifyTools
         │
         ├─ ① NotifyChannelRepository.findByName(channel)
         │     └─ SQLite notify_channels 表 → NotifyChannelEntity
         │
         ├─ ② 构建 NotifyTarget（type + config，含 ${ENV_VAR} 解析）
         │
         ├─ ③ Sandbox 白名单校验（TODO 占位）
         │
         └─ ④ NotifyChannelAdapter.send(target, content)
               │
               ├─ WebhookNotifyAdapter（核心阶段：HTTP POST）
               ├─ WechatWorkAdapter（扩展阶段）
               ├─ DingTalkAdapter（扩展阶段）
               └─ EmailAdapter（扩展阶段：SMTP）
```

**上游**：AgentService / ReActLoop（通过 ToolExecutor 调用 notify 工具）  
**下游**：NotifyChannelRepository（查渠道）、NotifyChannelAdapter（发送通知）、Sandbox（安全校验）

### 与入站 Channel 的关系

| 维度 | Channel Adapter（入站） | Notify（出站） |
|------|------------------------|---------------|
| 方向 | 外部 → Agent | Agent → 外部 |
| 职责 | 什么触发 Agent 开始跑 | Agent 跑完把结果送到哪 |
| 建模 | 分开建模，不合并 | 同一渠道可同时是入站和出站 |

### 核心职责（4 条）

1. 渠道注册表查询（SQLite `notify_channels` 表按名查找）
2. 通知发送（通过适配器模式，接口中立）
3. 安全校验（Sandbox 白名单，TODO 占位）
4. 凭证管理（`${ENV_VAR}` 环境变量替换）

---

## 第二段：动手之前先想清楚几件事

### 设计决策

| 决策 | 选择 | 为什么 |
|------|------|--------|
| 接口设计 | 只有一个 `send()` 方法 | 接口中立性——不绑定任何具体渠道类型，扩展零成本 |
| 参数命名 | `target` + `content` | `target` 不叫 `webhookUrl`，`content` 不叫 `text`——命名上不绑定具体渠道 |
| 渠道配置 | `NotifyTarget` 结构体（type + config Map） | 不同渠道配置差异大，Map 让每个适配器自己解读需要的 key |
| 渠道存储 | SQLite `notify_channels` 全局注册表 | Agent 只认名字，地址从注册表解析，修改渠道无需改 Agent |
| 凭证处理 | `${ENV_VAR}` 占位符 | 不明文落库，运行时从环境变量解析 |
| 错误处理 | 不静默失败 | 每一层失败都有明确信号向上传递 |
| 执行顺序 | 校验在前，发送在后 | inOrder 硬约束，Sandbox 校验必须在 HTTP 请求之前 |
| 适配器路由 | 适配器自己判断 type 是否匹配 | 接口保持中立，不强制 `getType()` 方法 |
| JSON 构建 | Jackson ObjectMapper | 手动拼字符串转义不健壮，Spring Boot 已有 Jackson 依赖 |
| 工具注册 | 渠道表为空时不注册 | LLM 看不到工具就不会尝试调用，从源头避免无效调用 |

### 关键约束

- **接口中性化**：`NotifyChannelAdapter` 接口只有一个业务方法 `send(NotifyTarget, String)`，不知道"类型"、"路由"这些概念
- **不静默失败**：5xx 抛异常、渠道不存在返回 fail、无配置返回 fail——每一层都必须把错误向上传递
- **校验在前，发送在后**：用 `InOrder` 测试钉死顺序，Sandbox 校验必须在 HTTP 请求之前
- **代码全英文，注释用中文**

### 避坑指南

| 陷阱 | 应对 |
|------|------|
| 适配器吞掉异常 | 非 2xx 响应抛 RuntimeException，不返回错误字符串 |
| URL 硬编码 | URL 从 `NotifyTarget.getConfig().get("url")` 读取，测试断言来自 target |
| JSON 拼接不安全 | 用 Jackson `ObjectMapper` 构建 payload，不手动拼字符串 |
| 渠道未配置时工具仍注册 | `notify_channels` 表为空时不注册 notify 工具，LLM 看不到 |
| 凭证明文落库 | config_json 中用 `${ENV_VAR}` 占位符，运行时替换 |
| 适配器接口加 `getType()` | 违反接口中立性，适配器自己判断 type 是否匹配 |

### 边界划分

**Notify 负责**：
- 渠道注册表查询（按名称查找 `notify_channels`）
- 构建 `NotifyTarget`（type + config，含 `${ENV_VAR}` 解析）
- 适配器路由（遍历所有 `NotifyChannelAdapter`，谁支持谁处理）
- 安全校验占位（Sandbox 白名单，TODO 标记）
- 错误向上传递（不静默失败）

**Notify 不负责**：
- 具体渠道 API 对接（企微签名、飞书加密等——各适配器实现）
- 渠道 CRUD 管理（Web API `/api/v1/notify-channels`，扩展阶段实现）
- 审计记录（走 ToolExecutor 现有审计路径，不新增逻辑）
- Sandbox 实现（复用现有 Sandbox 框架，核心阶段只占位）

---

## 第三段：代码怎么写

### 核心类清单

| 类名 | 职责 | 位置 |
|------|------|------|
| `NotifyTarget` | 通知目标结构体（type + config），不可变 record | `oryxos-tool/.../notify/` |
| `NotifyChannelAdapter` | 通知渠道适配器接口，仅 `send()` 一个方法 | `oryxos-tool/.../notify/` |
| `WebhookNotifyAdapter` | 通用 Webhook 适配器（HTTP POST），核心阶段唯一实现 | `oryxos-tool/.../notify/` |
| `NotifyTools` | 通知工具封装，内含 `NotifyTool`（OryxTool 实现） | `oryxos-tool/.../notify/` |
| `NotifyChannelEntity` | 渠道 JPA 实体（已存在） | `oryxos-storage/.../entity/` |
| `NotifyChannelRepository` | 渠道 Repository（已存在） | `oryxos-storage/.../repository/` |

### 关键接口设计

#### NotifyTarget（record）

```java
public record NotifyTarget(String type, Map<String, String> config) {}
```

不可变、轻量、纯数据载体。`type` 决定路由到哪个适配器，`config` 携带渠道特有配置。

#### NotifyChannelAdapter（接口）

```java
public interface NotifyChannelAdapter {
    String send(NotifyTarget target, String content);
}
```

只有一个业务方法，接口完全中立。适配器自己判断 `target.getType()` 是否匹配，不匹配返回 `"unsupported type: xxx"`。

### 各适配器从 config 中读取的 key 约定

| type | config 中的 key |
|------|----------------|
| webhook | `url` |
| smtp | `host`, `port`, `from`, `to`, `subject`, `username`, `password`, `encryption` |
| wecom | `url`（webhook 地址）, `secret`（签名校验） |
| feishu | `url`, `secret` |
| dingtalk | `url`, `secret` |

### 关键方法伪代码

#### NotifyTools.execute() — 核心编排

```java
@Override
public ToolResult execute(Map<String, Object> input) {
    String content = (String) input.get("content");
    String channelName = (String) input.get("channel");

    // ① 参数校验
    if (content == null || content.isBlank()) {
        return ToolResult.fail("content parameter is required", false);
    }

    // ② 渠道名解析：LLM 没传 → 取 Profile 默认值
    if (channelName == null || channelName.isBlank()) {
        Profile profile = ProfileContext.get();
        channelName = profile.getDefaultNotifyChannel();
    }
    if (channelName == null || channelName.isBlank()) {
        return ToolResult.fail(
            "No notification channel specified and no default configured.", false);
    }

    // ③ 从全局注册表查渠道
    Optional<NotifyChannelEntity> entity = channelRepository.findByName(channelName);
    if (entity.isEmpty()) {
        return ToolResult.fail("Channel not found: " + channelName, false);
    }

    // ④ 构建 NotifyTarget（含 ${ENV_VAR} 解析）
    NotifyTarget target = buildTarget(entity.get());

    // ⑤ TODO: Sandbox 白名单校验（校验在前，发送在后）
    // sandbox.enforce(new SandboxAction(HTTP_REQUEST, target.getConfig().get("url")));

    // ⑥ 遍历适配器，谁支持谁处理
    for (NotifyChannelAdapter adapter : adapters) {
        String result = adapter.send(target, content);
        if (result != null && !result.startsWith("unsupported")) {
            return ToolResult.ok(result);
        }
    }

    return ToolResult.fail(
        "No adapter supports channel type: " + target.getType(), false);
}
```

#### WebhookNotifyAdapter.send() — HTTP 发送

```java
@Override
public String send(NotifyTarget target, String content) {
    // 类型不匹配，交给下一个适配器
    if (!"webhook".equals(target.getType())) {
        return "unsupported type: " + target.getType();
    }

    String url = target.getConfig().get("url");
    if (url == null || url.isBlank()) {
        throw new IllegalArgumentException("webhook url not configured");
    }

    // TODO: Sandbox 白名单校验（占位）

    try {
        String payload = buildPayload(content);  // Jackson ObjectMapper

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response =
                HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return "Notification sent successfully (HTTP " + response.statusCode() + ")";
        }

        // 非 2xx：向上抛，不吞掉
        throw new RuntimeException(
                "Webhook returned HTTP " + response.statusCode() + ": " + response.body());

    } catch (IOException e) {
        throw new RuntimeException("Webhook request failed: " + e.getMessage(), e);
    }
}
```

#### NotifyTools.buildTarget() — 构建 NotifyTarget

```java
private NotifyTarget buildTarget(NotifyChannelEntity entity) {
    Map<String, String> config = new HashMap<>();
    config.put("url", entity.getUrl());
    if (entity.getConfigJson() != null) {
        Map<String, String> parsed = parseJson(entity.getConfigJson());
        parsed.forEach((k, v) -> config.put(k, resolveEnvVar(v)));  // TODO: resolveEnvVar
    }
    return new NotifyTarget(entity.getType(), config);
}
```

### 配置结构

#### Profile frontmatter 新增字段

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `default_notify_channel` | String | null | 默认通知渠道注册名，LLM 不传 channel 时使用 |

#### AGENT.md 示例

```yaml
---
name: weather
provider: deepseek
model: deepseek-chat
default_notify_channel: team-lark
schedules:
  - cron: "0 8 * * *"
    message: "查询今日天气并推送结果"
---
```

#### SQLite notify_channels 表（已存在）

| 字段 | 类型 | 说明 |
|------|------|------|
| name | VARCHAR(64) UNIQUE | 渠道注册名 |
| type | VARCHAR(32) | 渠道类型（webhook/smtp/wecom/feishu/dingtalk） |
| url | VARCHAR(512) | webhook URL |
| config_json | TEXT | 扩展配置 JSON（host/port/secret 等） |
| description | VARCHAR(256) | 渠道描述 |

### 数据流走向

```
用户输入："把测试消息推送一下"
  → ReActLoop → LLM 生成 tool_call: notify(content="测试消息")
    → ToolExecutor.execute("notify", input)
      → NotifyTools.execute(input)
        → ProfileContext.get().getDefaultNotifyChannel() → "team-lark"
        → channelRepository.findByName("team-lark") → NotifyChannelEntity
        → buildTarget(entity) → NotifyTarget(type=webhook, config={url:...})
        → TODO: sandbox.enforce(HTTP_REQUEST, url)
        → adapters 遍历 → WebhookNotifyAdapter.send(target, content)
          → HTTP POST to url with {"msgtype":"text","text":{"content":"测试消息"}}
          → 200 OK → return "Notification sent successfully"
        → ToolResult.ok("Notification sent successfully")
      → 审计落库 tool_invocations
    → LLM 看到 tool result，生成最终回复
  → 输出到用户
```

### 异常处理策略

| 异常场景 | 处理方式 | 是否静默 |
|----------|---------|---------|
| content 为空 | `ToolResult.fail("content parameter is required")` | 否 |
| 无默认渠道且 LLM 未指定 | `ToolResult.fail("No channel configured")` | 否 |
| 渠道名不存在 | `ToolResult.fail("Channel not found: xxx")` | 否 |
| url 未配置 | `throw IllegalArgumentException` | 否 |
| HTTP 5xx | `throw RuntimeException` | 否 |
| 网络异常 | `throw RuntimeException` (包装 IOException) | 否 |
| 无适配器支持该类型 | `ToolResult.fail("No adapter supports...")` | 否 |

**原则：不能静默失败。每一层失败都有明确信号向上传递。**

---

## 第四段：验收 harness：把验收标准变成可执行的测试

### 测试分层

| 层级 | 测试类 | 数量 | 说明 |
|------|--------|------|------|
| 单元测试 | WebhookNotifyAdapterTest | 5 | 适配器行为：类型匹配、发送成功/失败、异常处理、payload 格式 |
| 单元测试 | NotifyToolsTest | 5 | 工具编排：渠道查找、${ENV_VAR} 解析、适配器路由 |
| 单元测试 | AgentLoaderNotifyTest | 2 | frontmatter 解析 default_notify_channel（追加到现有测试） |
| **合计** | **3 个测试类** | **12** | |

### 关键测试用例

#### WebhookNotifyAdapterTest

| 用例名 | 输入 | 期望输出 | 验证点 |
|--------|------|---------|--------|
| `send_success` | type=webhook, config={url: mockUrl}, content="hello" | "Notification sent successfully (HTTP 200)" | HTTP POST 发出，payload 含 content |
| `send_typeMismatch` | type=smtp | "unsupported type: smtp" | 不发出任何 HTTP 请求 |
| `send_urlMissing` | type=webhook, config={} | 抛 IllegalArgumentException | 不发请求 |
| `send_http5xx` | mock 返回 500 | 抛 RuntimeException | 异常向上抛，不吞掉 |
| `send_urlComesFromTarget` | 两个不同 url 的 target | POST 发往不同路径 | URL 来自 NotifyTarget，不是硬编码 |

**测试数据准备**：用 JDK 内置 `com.sun.net.httpserver.HttpServer` 模拟 webhook 服务端，随机端口，验证请求 body 和路径。

#### NotifyToolsTest

| 用例名 | 输入 | 期望输出 | 验证点 |
|--------|------|---------|--------|
| `execute_channelNotFound` | channel="nonexistent" | ToolResult.fail("Channel not found") | 查注册表返回空 |
| `execute_success` | channel="team-lark", 适配器返回成功 | ToolResult.ok("Notification sent...") | 查到渠道 → 构建 target → 调适配器 |
| `execute_noMatchingAdapter` | type=smtp, 只有 webhook 适配器 | ToolResult.fail("No adapter supports...") | 所有适配器返回 unsupported |
| `execute_envVarResolution` | config_json 含 `${TEST_URL}` | NotifyTarget.config 里 url 被替换 | ${ENV_VAR} 解析正确 |
| `execute_contentBlank` | content="" | ToolResult.fail("content parameter is required") | 参数校验 |

**测试数据准备**：mock `NotifyChannelRepository`（返回预设 `NotifyChannelEntity`）+ mock `NotifyChannelAdapter`（控制返回成功/unsupported）。

#### AgentLoaderNotifyTest（追加到现有测试）

| 用例名 | 输入 | 期望输出 | 验证点 |
|--------|------|---------|--------|
| `deriveProfile_withDefaultNotifyChannel` | frontmatter 含 `default_notify_channel: team-lark` | profile.getDefaultNotifyChannel() == "team-lark" | 解析正确 |
| `deriveProfile_withoutDefaultNotifyChannel` | frontmatter 不含该字段 | profile.getDefaultNotifyChannel() == null | 不报错，默认 null |

### InOrder 测试（钉死执行顺序）

```java
@Test
@DisplayName("Sandbox 校验在 HTTP 发送之前执行")
void sandboxCheckBeforeSend() {
    // ...
    InOrder order = inOrder(sandbox, httpClient);
    order.verify(sandbox).enforce(eq(HTTP_REQUEST), eq(url));
    order.verify(httpClient).send(any(), any());
}

@Test
@DisplayName("Sandbox 校验不通过时，HTTP 请求不发出")
void sandboxRejectBlocksSend() {
    // ...
    verify(httpClient, never()).send(any(), any());
}
```

### 运行命令

```bash
# Notify 相关单元测试
mvn test -pl oryxos-tool -Dtest="WebhookNotifyAdapterTest,NotifyToolsTest"

# AgentLoader frontmatter 解析
mvn test -pl oryxos-core -Dtest="AgentLoaderTest"

# 全量回归
mvn clean verify
```

---

## 第五段：做完怎样验

### 人工确认项

| # | 验收项 | 操作 | 预期结果 | 类型 |
|---|--------|------|---------|------|
| 1 | 真实 webhook 端到端 | 在企微/飞书群创建机器人 → 注册渠道 → Agent 调 notify | 群里收到消息 | blocking |
| 2 | 渠道未配置报错 | 不注册任何渠道 → Agent 调 notify | 返回 "Channel not found" 或 "No channel configured" | blocking |
| 3 | 白名单先行 | 检查代码 TODO 占位 | `WebhookNotifyAdapter.send()` 中 Sandbox 校验在 HTTP 请求之前 | blocking |
| 4 | 回归不破 | `mvn clean verify` | BUILD SUCCESS，0 failures | blocking |
| 5 | 接口中立性 | 写出 WechatWorkAdapter / DingTalkAdapter 骨架 | 只实现 `NotifyChannelAdapter` 即可，接口和 NotifyTools 不动 | blocking |
| 6 | 5xx 不静默 | mock server 返回 500 | 抛 RuntimeException，不返回错误字符串 | blocking |
| 7 | URL 来自 target | 用不同 url 构造 NotifyTarget | 每次 POST 目标地址跟随 target 变化 | blocking |

### 自动化验收标准

```bash
# 1. 编译打包
mvn clean package

# 2. 单元测试全绿
mvn test

# 3. 完整验证（含 Spotless/Checkstyle/SpotBugs）
mvn clean verify

# 期望：BUILD SUCCESS，0 failures, 0 errors
```

### 与 agents.md 原则对齐

| 原则 | 对齐情况 |
|------|---------|
| H1: 代码全英文，注释用中文 | ✅ 遵守 |
| H2: 传统 switch-case | ✅ 无增强 switch |
| H3: 凭证走环境变量 | ✅ ${ENV_VAR} 占位符 |
| H4: 不用 ChatClient/Advisor | ✅ 只调 AgentService |
| H5: 测试方法名英文，@DisplayName 中文 | ✅ 遵守 |
| H6: mvn clean verify 全绿 | ✅ 目标 |
| 不能静默失败 | ✅ 每一层错误都向上传递 |
| 校验在前，发送在后 | ✅ InOrder 测试钉死 |

### 性能/安全/可观测性

| 维度 | 验收点 | 类型 |
|------|--------|------|
| 性能 | webhook 发送超时 10s，不阻塞主线程 | non-blocking |
| 安全 | Sandbox 域名白名单校验（TODO 占位） | blocking |
| 安全 | 凭证不明文落库（${ENV_VAR}） | blocking |
| 可观测性 | 发送失败 log.error 含上下文 | non-blocking |
| 可观测性 | 审计记录 tool_invocations 含成功/失败 | non-blocking |

---

## 变更记录

| 日期 | 变更内容 | 变更人 |
|------|---------|--------|
| 2026-10-22 | 初始版本：Notify 模块设计文档 | AI |
