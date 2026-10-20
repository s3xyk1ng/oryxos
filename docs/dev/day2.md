# ReAct 模块设计文档

## 第一段：模块是什么，干嘛用的

### 模块定位

ReAct 模块是 OryxOS 的**引擎层**——整个 Agent 系统的大脑。它实现 Reason + Act 循环：接收用户消息，反复调用 LLM 思考、执行工具、观察结果，直到 LLM 给出最终回复或达到最大轮数。

### 在系统中的位置

```
CLI Channel ──┐
REST API ─────┼──→ AgentService ──→ ReActLoop ──→ ProviderService（Day 1）
Scheduler ────┘         │              │
                        │              ├──→ PromptBuilder
                        │              ├──→ ToolExecutor
                        │              │
                        │              └──→ ContextLoader
                        │
                        ├──→ SessionManager（持久化）
                        └──→ ProfileContext（ThreadLocal）
```

- **上游**：三种触发入口（CLI / REST / Scheduler）通过 `AgentService.process()` 统一调用
- **下游**：ReActLoop 调用 `ProviderService`（LLM 调用）、`PromptBuilder`（Prompt 组装）、`ToolExecutor`（工具执行）

### 核心职责

1. **ReActLoop**：驱动 Reason-Act 循环，严格控制最大迭代次数
2. **AgentService**：统一入口，管理 ProfileContext 生命周期和 Session 持久化
3. **PromptBuilder**：四段拼接 Prompt（system prompt + 记忆 + 历史 + 工具 schema）
4. **ToolExecutor**：查找工具 → Sandbox 检查 → 执行 → 审计（无论成败都记录）
5. **ContextLoader**：加载 AGENT.md / Bootstrap / Skills 上下文文件

---

## 第二段：动手之前先想清楚几件事

### 设计决策

| # | 决策 | 选择 | 理由 |
|---|------|------|------|
| 1 | ReActLoop 是否持有循环 | **是** | 循环控制 + 消息累积 + 终止判断是一个原子逻辑，拆开会导致状态散落各处 |
| 2 | ReActLoop 是否接收 `List<OryxTool>` | **否** | Tool 查找由 ToolExecutor 内部从 ToolRegistry 获取，ReActLoop 不感知工具列表 |
| 3 | LLM 调用用什么类型 | **ProviderService**（OryxOS 原生类型） | 不再直接依赖 Spring AI 的 ChatResponse/Prompt，彻底解耦 |
| 4 | LLM 审计谁做 | **ProviderService 内部的 AuditLogger** | Day 1 已实现，ReActLoop 不再重复审计 |
| 5 | Tool 审计谁做 | **ToolExecutor** | 在 finally 块中记录，无论成功/失败/找不到/沙箱拒绝都写一条 |
| 6 | ProfileContext 怎么传 | **ThreadLocal** | 虚拟线程下每个请求天然独立，Tool 执行时通过 ProfileContext.get() 读取当前 Agent |

### 硬约束

| 约束 | 值 | 说明 |
|------|-----|------|
| `maxIterations` | 默认 10 | Profile 可覆盖，防止 Agent 陷入工具调用死循环 |
| `maxHistoryTurns` | 默认 20 | 一轮 = user + assistant = 2 条消息，20 轮 = 最多 40 条 |
| Prompt 末尾 | 必须附当前日期时间 | RFC_1123 格式，LLM 不知道今天几号，定时场景全靠这一行 |
| 代码语法 | 传统 switch-case | 禁止增强 switch（`case -> `），P3C/ASM 解析不了 |
| 代码语言 | 全英文 | 注释用中文，代码标识符用英文 |
| 审计 | 工具调用无论成败都记录 | success + error_message 字段区分 |

### 边界划分

**ReActLoop 负责的：**
- for 循环 + maxIterations 限制
- 消息追加（user / assistant / tool）到 Session
- 调用 PromptBuilder 组装 Prompt
- 调用 ProviderService 调 LLM
- 判断 hasToolCalls 决定继续还是终止
- 遍历 toolCalls 调 ToolExecutor 执行

**ReActLoop 不负责的：**
- ❌ LLM 审计（ProviderService 内置）
- ❌ Tool 查找（ToolExecutor 内部从 ToolRegistry 获取）
- ❌ Session 持久化（AgentService 负责）
- ❌ ProfileContext 管理（AgentService 负责）
- ❌ 记忆读写（PromptBuilder 通过 MemoryPort 处理）

### 核心阶段明确不做

- ❌ 工具并行调用（tool_calls 按顺序执行）
- ❌ Agent 之间相互委托
- ❌ 流式输出（扩展阶段用 SSE）
- ❌ 上下文压缩（核心阶段只截断，不总结）

### 避坑指南

| 坑 | 应对 |
|----|------|
| 增强 switch 语法违规 | 全部用传统 `case: ... break;` |
| 重复审计（LLM 审计写两条） | ReActLoop 移除 AuditPort，LLM 审计交给 ProviderService |
| 上下文撑爆 token | PromptBuilder 按 maxHistoryTurns 截断对话历史 |
| Tool 找不到时中断循环 | 返回 ToolResult.fail(error, false)，错误信息作为 tool 消息让 LLM 自行修正 |
| ProfileContext 泄漏 | AgentService finally 块必须 clear()，单测验证请求结束后 get() == null |
| AGENT.md 缺失静默降级 | 抛 AgentConfigException，fail-fast |
| Bootstrap 缺失人格丢失 | log.warn 告警 |

---

## 第三段：代码怎么写

### 核心类清单

| 类名 | 包路径 | 模块 | 职责 | 操作 |
|------|--------|------|------|------|
| `ReActLoop` | `com.oryxos.core.react` | oryxos-core | ReAct 循环引擎 | 重写 |
| `AgentService` | `com.oryxos.core.agent` | oryxos-core | 统一入口编排 | 改造 |
| `PromptBuilder` | `com.oryxos.core.react` | oryxos-core | Prompt 四段拼接 | 改造 |
| `ToolExecutor` | `com.oryxos.core.react` | oryxos-core | 工具执行 + 审计 | 改造 |
| `ContextLoader` | `com.oryxos.core.context` | oryxos-core | 上下文文件加载 | 改造 |
| `ProfileContext` | `com.oryxos.core.agent` | oryxos-core | ThreadLocal Profile | 新建 |
| `AgentConfigException` | `com.oryxos.core.agent` | oryxos-core | Agent 配置缺失异常 | 新建 |
| `ToolInvocationEntity` | `com.oryxos.storage.entity` | oryxos-storage | 工具调用审计实体 | 改造（加 toolCallId） |
| `ToolInvocationRepository` | `com.oryxos.storage.repository` | oryxos-storage | 工具调用审计查询 | 改造（加 findByToolCallId） |
| `Message` | `com.oryxos.core.model` | oryxos-core | 会话消息 | 改造（加 toolCallId） |

### 删除文件

| 文件 | 理由 |
|------|------|
| `LlmPort.java` | 被 ProviderService 替代 |
| `LlmPortFactory.java` | 被 ProviderService 替代 |
| `ProviderLlmPortFactory.java` | Day 1 过渡桥接，不再需要 |
| `ToolCallParser.java` | tool_call 提取已在 ProviderService 内完成 |
| `ToolCallRequest.java` | 被 Day 1 的 ToolCall record 替代 |

### 关键方法签名与伪代码

#### ReActLoop.run()

```java
public ProviderResponse run(Session session, Profile profile, String message) {
    // 1. 用户消息追加（首次传入，后续 null）
    if (message != null && !message.isBlank()) {
        session.addMessage(new Message("user", message));
    }

    int maxIterations = profile.getMaxIterations();
    ProviderResponse response = null;

    // 2. 循环推理，严格控制最大次数
    for (int iteration = 0; iteration < maxIterations; iteration++) {
        // 组装 Prompt（委托 PromptBuilder）
        Prompt prompt = promptBuilder.build(profile, session);

        // 调用 LLM（委托 ProviderService，内置审计）
        response = providerService.chat(session.getSessionId(), profile, prompt);

        // 助手响应追加到会话历史
        session.addMessage(new Message("assistant", response.content()));

        // 无 tool_calls → 推理结束
        if (!response.hasToolCalls()) {
            return response;
        }

        // 有 tool_calls → 逐个执行（委托 ToolExecutor）
        for (ToolCall tc : response.toolCalls()) {
            ToolResult result = toolExecutor.execute(session, tc);
            session.addMessage(new Message("tool", "[" + tc.name() + "] " + result.getContent()));
        }
    }

    // 3. 达到最大轮数，强制返回
    log.warn("Max iterations ({}) reached, session={}", maxIterations, session.getSessionId());
    return response;
}
```

#### AgentService.process()

```java
public String process(Session session, String message) {
    Profile profile = sessionManager.getProfile(session);
    if (profile == null) {
        throw new IllegalArgumentException("Profile not found: " + session.getSessionId());
    }

    ProfileContext.set(profile);
    try {
        ProviderResponse response = reActLoop.run(session, profile, message);
        sessionManager.save(session);
        return response.content();
    } catch (Exception e) {
        log.error("Agent process failed, session={}", session.getSessionId(), e);
        throw e;
    } finally {
        ProfileContext.clear();  // 必须清理，防止虚拟线程复用时 Profile 泄漏
    }
}
```

#### PromptBuilder.build()

```java
public Prompt build(Profile profile, Session session) {
    // ① System Prompt = Bootstrap + AGENT.md + Skills + 当前时间
    String systemPrompt = buildSystemPrompt(profile, session);

    // ② Memory 注入（通过 MemoryPort，可选）
    // 已在 buildSystemPrompt 内处理

    // ③ 对话历史按 maxHistoryTurns 截断
    List<Message> truncatedHistory = truncateHistory(session.getMessages(), profile.getMaxHistoryTurns());

    // ④ Tool 列表从 ToolRegistry 获取，生成 Function Calling schema
    List<OryxTool> tools = toolRegistry.getToolsForProfile(profile);

    // 组装 Spring AI Prompt（内部处理消息类型映射）
    return assemblePrompt(systemPrompt, truncatedHistory, tools);
}
```

#### ToolExecutor.execute()

```java
public ToolResult execute(Session session, ToolCall toolCall) {
    String toolName = toolCall.name();
    Map<String, Object> input = toolCall.arguments();
    long start = System.currentTimeMillis();
    ToolResult result = null;

    try {
        // 1. 从 ToolRegistry 查找
        OryxTool tool = toolRegistry.find(toolName);
        if (tool == null) {
            result = ToolResult.fail("Tool not found: " + toolName, false);
            return result;
        }

        // 2. Sandbox 检查
        if (sandbox != null) {
            try {
                performSandboxCheck(tool, input);
            } catch (SecurityException e) {
                result = ToolResult.fail("Sandbox rejected: " + e.getMessage(), false);
                return result;
            }
        }

        // 3. 执行
        try {
            result = tool.execute(input);
        } catch (Exception e) {
            result = ToolResult.fail("Execution error: " + e.getMessage(), true);
        }
        return result;
    } finally {
        // 4. 审计：无论成败，四种情况全覆盖
        long duration = System.currentTimeMillis() - start;
        if (result != null) {
            auditPort.recordToolInvocation(
                session.getSessionId(), toolCall.id(), toolName,
                mapToJson(input),
                result.isSuccess() ? result.getContent() : result.getError(),
                result.isSuccess(),
                result.isSuccess() ? null : result.getError(),
                duration);
        }
    }
}
```

#### ContextLoader 错误约束

```java
// AGENT.md 缺失 → ERROR + 抛异常
public String loadAgentBody(String agentName) {
    Path agentMd = workspaceRoot.resolve(".oryxos/agents/" + agentName + "/AGENT.md");
    if (!Files.exists(agentMd)) {
        throw new AgentConfigException("AGENT.md not found for agent: " + agentName);
    }
    // 读取并去除 frontmatter...
}

// Bootstrap 缺失 → WARN
public String loadBootstrap() {
    for (String file : List.of("AGENTS.md", "SOUL.md", "USER.md")) {
        Path path = oryxosDir.resolve(file);
        if (Files.exists(path)) {
            // 读取追加
        } else {
            log.warn("Bootstrap file missing: {}", path);  // 人格丢失告警
        }
    }
}
```

#### ProfileContext

```java
public final class ProfileContext {
    private static final ThreadLocal<Profile> CURRENT = new ThreadLocal<>();

    private ProfileContext() {}

    public static void set(Profile profile) { CURRENT.set(profile); }
    public static Profile get() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
```

### 数据模型变更

| 模型 | 变更 |
|------|------|
| `Message` | 新增 `toolCallId` 字段（tool 消息关联对应的 tool_call） |
| `Profile` | 确认 `maxIterations`（默认 10）、`maxHistoryTurns`（默认 20）字段存在 |
| `ToolInvocationEntity` | 新增 `toolCallId` 字段（VARCHAR(64)） |
| `ToolInvocationRepository` | 新增 `findByToolCallId(String toolCallId)` 方法 |
| `V5__add_tool_call_id.sql` | `ALTER TABLE tool_invocations ADD COLUMN tool_call_id VARCHAR(64)` |

### 数据流走向

```
用户消息
  → AgentService.process(session, message)
    → ProfileContext.set(profile)
    → ReActLoop.run(session, profile, message)
      → PromptBuilder.build(profile, session)
        → ContextLoader.loadBootstrap() + loadAgentBody() + loadSkills()
        → MemoryPort.getMemoryContext()
        → Session.getMessages() 截断
        → ToolRegistry.getToolsForProfile() → schema 生成
      → ProviderService.chat(sessionId, profile, prompt)
        → ProviderRegistry.find() → ChatModelFactory.resolve() → ChatModel.call()
        → AuditLogger 记录 llm_calls
      → 判断 hasToolCalls
        → 无：return response
        → 有：ToolExecutor.execute(session, toolCall)
          → ToolRegistry.find() → Sandbox → tool.execute()
          → AuditLogger 记录 tool_invocations（finally）
      → 循环...
    → SessionManager.save(session)
    → ProfileContext.clear()
  → 返回最终回复
```

---

## 第四段：验收 harness：把验收标准变成可执行的测试

### 测试分层

| 层级 | 数量 | 工具 | 说明 |
|------|------|------|------|
| 单元测试 | 48 个验收点 | JUnit 5 + Mockito | 全部 mock 外部依赖，不涉及网络 |
| 人工验证 | 10 项 | 手动 + 代码审查 | 端到端 + Demo + 代码合规 |

### 单测验收矩阵

#### ReActLoopTest（8 个验收点）

| # | 测试点 | 验证内容 | 类型 |
|---|--------|---------|------|
| 1 | 无 tool_call 直接返回 | 调一次 LLM，响应无 toolCalls → 返回 content，循环 1 次结束 | 功能 |
| 2 | 有 tool_call 继续循环 | LLM 返回 toolCalls → 调 ToolExecutor → 结果追加到 Session → 进入下一轮 | 功能 |
| 3 | 最大轮数强制终止 | 每次 LLM 都返回 toolCalls → 达到 maxIterations 后强制返回最后一次响应 | 功能 |
| 4 | 用户消息追加到 Session | run() 后 Session.messages 第一条是 role=user | 功能 |
| 5 | 助手消息每轮追加 | 3 轮循环后 Session 中有 3 条 role=assistant 消息 | 功能 |
| 6 | 工具结果追加到 Session | tool_call 执行后 Session 中有 role=tool 消息，格式 `[toolName] content` | 功能 |
| 7 | message 为 null 不追加 | 后续迭代传 null → Session 不多出 user 消息 | 功能 |
| 8 | 回归：模型一直调工具直到强制停止 | mock LLM 每次都返回 toolCalls → 循环跑满 maxIterations → 强制返回，不抛异常、不无限循环 | 回归 |

#### AgentServiceTest（6 个验收点）

| # | 测试点 | 验证内容 | 类型 |
|---|--------|---------|------|
| 1 | 正常流程 | process() 调 reActLoop.run() → 返回 response.content() | 功能 |
| 2 | ProfileContext set/clear | process() 执行前 ProfileContext.get() 有值，执行后为 null | 功能 |
| 3 | Session 持久化 | process() 完成后 sessionManager.save() 被调用 | 功能 |
| 4 | Profile 不存在抛异常 | sessionManager.getProfile() 返回 null → 抛 IllegalArgumentException | 功能 |
| 5 | 异常时 ProfileContext 清理 | reActLoop.run() 抛异常 → finally 中 ProfileContext.clear() 仍执行 | 功能 |
| 6 | 回归：请求结束后 ProfileContext 无泄漏 | 正常请求和异常请求分别执行后，断言 ProfileContext.get() == null；若 finally 缺少 clear()，get() 残留旧 Profile → 断言失败 | 回归 |

#### PromptBuilderTest（9 个验收点）

| # | 测试点 | 验证内容 | 类型 |
|---|--------|---------|------|
| 1 | System Prompt 四段拼接 | 结果包含 bootstrap + agentBody + skills + 时间 | 功能 |
| 2 | 当前日期时间在末尾 | system prompt 末尾包含 `Current time:` + RFC_1123 格式时间 | 功能 |
| 3 | 对话历史截断 | 50 条消息 + maxHistoryTurns=20 → 只取最近 40 条 | 功能 |
| 4 | Memory 注入 | MemoryPort 返回内容 → 拼入 system prompt | 功能 |
| 5 | Memory 为空不注入 | MemoryPort 返回 null/空 → system prompt 不含 memory 段 | 功能 |
| 6 | MemoryPort 不存在 | memoryPort=null → 不报错，跳过 memory 段 | 功能 |
| 7 | Tool schema 生成 | 有 tools → prompt 中包含 function calling 格式的工具定义 | 功能 |
| 8 | 无 tools | tools 为空 → prompt 中不含工具段 | 功能 |
| 9 | 传统 switch-case | 消息 role 映射用传统 switch，不用增强 switch | 功能 |

#### ToolExecutorTest（10 个验收点）

| # | 测试点 | 验证内容 | 类型 |
|---|--------|---------|------|
| 1 | 工具执行成功 | 返回 ToolResult(success=true)，内容为工具返回值 | 功能 |
| 2 | 工具找不到 | 返回 ToolResult(success=false, retryable=false)，error="Tool not found: xxx" | 功能 |
| 3 | Sandbox 拒绝 | 返回 ToolResult(success=false, retryable=false)，error 包含 "Sandbox rejected" | 功能 |
| 4 | 工具执行抛异常 | 返回 ToolResult(success=false, retryable=true)，error 包含异常信息 | 功能 |
| 5 | 成功时审计记录 | auditPort 被调用，success=true，errorMessage=null | 功能 |
| 6 | 找不到时审计记录 | auditPort 被调用，success=false，errorMessage="Tool not found: xxx" | 功能 |
| 7 | Sandbox 拒绝时审计记录 | auditPort 被调用，success=false | 功能 |
| 8 | 异常时审计记录 | auditPort 被调用，success=false，retryable=true | 功能 |
| 9 | 审计包含 toolCallId | auditPort 调用时传入了正确的 toolCallId | 功能 |
| 10 | 审计包含 duration | auditPort 调用时 duration_ms ≥ 0 | 功能 |

#### ContextLoaderTest（9 个验收点）

| # | 测试点 | 验证内容 | 类型 |
|---|--------|---------|------|
| 1 | Bootstrap 三个文件都存在 | 返回拼接内容 | 功能 |
| 2 | Bootstrap 部分缺失 | 缺失文件 → log WARN，其余正常返回 | 功能 |
| 3 | Bootstrap 全部缺失 | 全部 WARN，返回空字符串 | 功能 |
| 4 | AGENT.md 正常加载 | 返回去掉 frontmatter 后的正文 | 功能 |
| 5 | AGENT.md 不存在 | 抛 AgentConfigException | 功能 |
| 6 | AGENT.md 无 frontmatter | 返回完整内容（不截断） | 功能 |
| 7 | Skills 扫描 | 读取 skills/ 目录下 SKILL.md 第一行作为 description | 功能 |
| 8 | Skills 目录不存在 | 返回空列表 | 功能 |
| 9 | 无缓存回归 | 第一次读取内容为 A → 修改文件为 B → 第二次读取立即拿到 B | 回归 |

#### ProfileContextTest（4 个验收点）

| # | 测试点 | 验证内容 | 类型 |
|---|--------|---------|------|
| 1 | set + get | set 后 get 返回同一 Profile | 功能 |
| 2 | clear 后 get 为 null | clear() 后 get() 返回 null | 功能 |
| 3 | 覆盖 set | 连续 set 两次 → get 返回最后一次的值 | 功能 |
| 4 | 线程隔离 | 线程 A set profileA，线程 B set profileB → 各自 get 到自己的值 | 功能 |

### 运行命令

```bash
# 运行全部单测
mvn test -pl oryxos-core

# 运行指定测试类
mvn test -pl oryxos-core -Dtest="ReActLoopTest"
mvn test -pl oryxos-core -Dtest="AgentServiceTest"
mvn test -pl oryxos-core -Dtest="PromptBuilderTest"
mvn test -pl oryxos-core -Dtest="ToolExecutorTest"
mvn test -pl oryxos-core -Dtest="ContextLoaderTest"
mvn test -pl oryxos-core -Dtest="ProfileContextTest"
```

---

## 第五段：做完怎样验

### 人工验证清单

| # | 验证项 | 怎么验 | 通过标准 | Blocking |
|---|--------|--------|---------|----------|
| 1 | 端到端冒烟 | 启动应用，通过 CLI/REST 发消息 | 消息进 → LLM 响应 → 最终回复，全链路通 | ✅ |
| 2 | 真实 LLM 调用 | 用 DeepSeek API，观察 LLM 正常返回 | ProviderService 真实 ChatModel 能跑通 | ✅ |
| 3 | 真实 Tool 执行 | 让 LLM 触发内置 Tool | ToolRegistry → Sandbox → 执行 → 回填全链路通 | ✅ |
| 4 | 审计落库 | 查 SQLite `llm_calls` + `tool_invocations` 表 | 真实调用后有记录，字段正确 | ✅ |
| 5 | Prompt 含当前时间 | 让 LLM 回答"今天几号" | 时间戳确实被 LLM 读到 | ✅ |
| 6 | Demo：每日天气对话 | 真模型跑通天气对话版：问天气 → Agent 调 http_get → 拿到数据 → 给穿搭建议 | 多轮对话里 Agent 调了 http_get、拿到数据、给出建议 | ✅ |
| 7 | AGENT.md 缺失报错 | 临时删掉 AGENT.md，发消息 | 抛 AgentConfigException，不静默降级 | ✅ |
| 8 | 无禁用项（代码审查） | 全局搜索：无 ChatClient / Advisor / Agent 抽象 / 增强 switch；循环是自实现的 | 硬门禁 + 架构合规 | ✅ |
| 9 | 旧代码清理干净 | 确认 5 个旧文件全部删除，无残留引用 | 编译通过 | ✅ |
| 10 | harness 覆盖项 | 死循环兜底、消息累积、历史截断、失败审计 | mvn test 全绿即打勾 | ✅ |

### 自动化验收命令

```bash
# 全量构建 + 单测
mvn clean verify

# 期望结果：BUILD SUCCESS，0 failures，0 errors
```

### 交付文件总览

| 类别 | 数量 | 明细 |
|------|------|------|
| 代码改造 | 7 | ReActLoop, AgentService, PromptBuilder, ToolExecutor, ContextLoader, ToolInvocationEntity, ToolInvocationRepository |
| 代码新建 | 4 | ProfileContext, AgentConfigException, V5 迁移脚本, Message.toolCallId |
| 删除 | 5 | LlmPort, LlmPortFactory, ProviderLlmPortFactory, ToolCallParser, ToolCallRequest |
| 单测 | 6 | 48 个验收点 |
| 人工验证 | 10 | 端到端 + Demo + 代码审查 + harness 覆盖项 |
| **总计** | **23 个文件变动** | |

---

## 变更记录

| 日期 | 变更内容 | 变更人 |
|------|---------|--------|
| 2026-10-16 | 初始版本：ReAct 模块完整设计 | 子非鱼 |
