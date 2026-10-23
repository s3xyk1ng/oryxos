# Tool 模块设计文档

## 第一段：模块是什么，干嘛用的

**一句话定位**：Tool 模块是 OryxOS Agent 的能力扩展层——Agent 能做什么，取决于注册了哪些工具。

### 模块角色

Tool 模块统一管理 Agent 可调用的所有外部能力：内置工具（文件 / Shell / HTTP）、通知推送（Notify）、MCP 外部工具、以及 `@Tool` 注解的自定义 Java 工具。所有工具对 ReAct 循环来说都是统一的 `OryxTool` 接口，循环引擎不感知工具来源。

### 在系统中的位置

```
接入层（CLI / Web / Scheduler）
    │
    ▼
引擎层（ReActLoop）
    │  调用工具
    ▼
能力层（Tool 模块）
    ├── ToolRegistry         ← 注册表（所有工具的统一入口）
    ├── ToolExecutor         ← 执行器（查找 → 执行 → 兜底 → 审计）
    ├── 内置工具              ← FileTools / ShellTools / HttpTools
    ├── NotifyTool            ← 通知推送（条件注册）
    ├── MCP 工具              ← McpLoader → McpToolAdapter
    └── @Tool 注解工具        ← ToolMethodScanner → AnnotatedToolAdapter
    │
    ▼
基础层（Sandbox / AuditPort / SQLite）
```

### 核心职责

1. **统一接口**：所有工具实现 `OryxTool`（getName / getDescription / getInputSchema / execute）
2. **统一注册**：所有工具注册到 `ToolRegistry`，支持 Profile 级白名单过滤
3. **统一执行**：`ToolExecutor` 负责查找 → 执行 → 兜底异常 → 审计落库
4. **条件注册**：工具可实现 `ConditionalTool` 接口，不满足条件时不注册
5. **安全自治**：每个 Java 工具在 `execute()` 第一行自行调用 Sandbox 校验

### 上下游

| 方向 | 模块 | 关系 |
|------|------|------|
| 上游 | ReActLoop | 调用 ToolExecutor 执行工具 |
| 上游 | Provider 层 | 把 `List<OryxTool>` 翻译成 LLM 的 function calling 格式；解析 LLM 的 tool_calls；格式化 ToolResult 回填 |
| 下游 | Sandbox | 工具自身调用 `sandbox.enforce()` 做安全校验 |
| 下游 | AuditPort | ToolExecutor 记录每次工具调用到 `tool_invocations` 表 |

---

## 第二段：动手之前先想清楚几件事

### 决策 1：Sandbox 校验下沉到每个工具自身

| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| A：ToolExecutor 按工具名前缀路由 | 工具不用注入 Sandbox | 新增工具要改 ToolExecutor；路由逻辑脆弱 | ❌ |
| B：每个工具自己在 execute() 开头调 Sandbox | 工具自治，新增工具不改 ToolExecutor | 每个工具都要注入 Sandbox | ✅ |

**为什么选 B**：新增工具不应该改基础设施。每个工具最清楚自己该做什么类型的校验（FILE_READ / FILE_WRITE / SHELL_COMMAND / HTTP_REQUEST）。ToolExecutor 职责简化为纯粹的"查找 → 执行 → 兜底 → 审计"。

### 决策 2：条件注册——ConditionalTool 接口

当工具依赖的外部条件不满足时（如通知渠道表为空），工具不注册到 ToolRegistry，LLM 看不到它。

```java
public interface ConditionalTool {
    boolean isAvailable();
}
```

`ToolAutoRegistrar` 注册时检查：实现了 `ConditionalTool` 的工具，`isAvailable()` 返回 false 则跳过。

### 决策 3：NotifyTool 从内部类提为独立组件

当前 `NotifyTool` 是 `NotifyTools` 的内部类，靠外部类访问 `channelRepository`。重构为独立 `@Component`，实现 `OryxTool` + `ConditionalTool`，统一走 `ToolAutoRegistrar` 注册。`NotifyTools` 包装类删除。

### 决策 4：`@Tool` 注解方式——ToolMethodScanner + AnnotatedToolAdapter

Plugin Tool 重代码路径：Java 开发者用 `@Tool` 注解标注方法，框架启动时扫描并注册。

```
@Tool 方法 → ToolMethodScanner 扫描 → AnnotatedToolAdapter 包装 → ToolRegistry
```

`AnnotatedToolAdapter` 实现 `OryxTool`，通过反射调用原方法。

### 决策 5：Plugin Tool 三档是文档概念，代码层统一

| 档位 | 谁写代码 | 技术形态 | 典型场景 |
|------|---------|---------|---------|
| 零代码 | 业务人员 | AGENT.md + 复用现成 MCP Server | 接天气查询 |
| 轻代码 | 企业开发 | 任意语言写 MCP Server | 对接内部 CRM |
| 重代码 | Java 开发 | `@Tool` 注解 Java Spring Bean | NotifyTool 深度集成 |

代码层面所有工具统一 `OryxTool` 接口，注册进同一个 `ToolRegistry`，被 ReAct 循环无差别消费。不加 `ToolOrigin` 枚举。

### 决策 6：MCP 工具核心阶段不做 Sandbox

MCP 是外部进程，OryxOS 不知道它要做什么，无法映射到白名单。安全边界在 MCP Server 自身和网络层。核心阶段不做框架级 Sandbox 校验。

### 决策 7：`@Tool` 注解工具的 Sandbox——工具自己负责

和内置工具策略一致，`@Tool` 方法内部自行调用 `sandbox.enforce()`，框架不自动加校验。

### 决策 8：重试策略——决策权在模型

| 组件 | 职责 |
|------|------|
| ToolExecutor | 执行一次 + 兜底异常 → ToolResult（含 retryable 标记） |
| ReActLoop | 把 ToolResult 交给模型 → 模型自己决定下一步 |
| maxIterations | 兜底防护，默认 10 |

`retryable` 字段是给模型的提示信号：
- `retryable=true` → 错误信息附带 `[retryable]`，模型可能选择重试
- `retryable=false` → 错误信息附带 `[not retryable]`，模型应该换方案

连续失败防护（方案 B：检测重复调用追加提示）放后续完善阶段，核心阶段靠 `maxIterations` 兜底。

### 决策 9：工具调用失败不拖垮流程

- ToolExecutor 兜底所有异常，返回 `ToolResult.fail()`，不向上抛
- ReActLoop 的 for 循环内加 try-catch 防御，一个工具失败不影响同批次其他工具
- 失败结果写入对话历史，模型看到后自行判断

### 关键约束

| 约束 | 说明 |
|------|------|
| OryxTool 不知道 Spring AI | 只接收 `Map<String, Object>` input，返回 `ToolResult` |
| 解析和回填归 Provider 层 | Tool 模块不管 LLM 协议转换 |
| 传统 switch-case | 不用增强 switch（P3C/ASM 限制） |
| 代码全英文，注释用中文 | 项目规范 |
| 测试方法名英文，@DisplayName 中文 | 项目规范 |
| mvn clean verify 全绿 | 交付标准 |

### 边界划分

| 模块负责 | 模块不负责 |
|---------|-----------|
| 工具注册、查找、执行 | LLM 协议转换（Provider 层） |
| Sandbox 校验（Java 工具自身） | Sandbox 实现本身 |
| 异常兜底、审计落库 | 重试决策（模型负责） |
| 条件注册（ConditionalTool） | 连续失败检测（后续完善） |

---

## 第三段：代码怎么写

### 核心类清单

#### oryxos-core（接口 + 引擎）

| 类名 | 包 | 动作 | 职责 |
|------|---|------|------|
| `OryxTool` | core.model | 不变 | 统一工具接口：getName / getDescription / getInputSchema / execute |
| `ToolResult` | core.model | 不变 | 执行结果：ok / fail，含 success / content / error / retryable |
| `ConditionalTool` | core.model | **新增** | 条件注册接口：`boolean isAvailable()` |
| `ToolRegistry` | core.agent | 不变 | ConcurrentHashMap 注册表，支持 Profile 级白名单过滤 |
| `ToolExecutor` | core.react | **改** | 删除 `performSandboxCheck()`；失败信息拼接 `[retryable]` / `[not retryable]` |

#### oryxos-tool（实现）

| 类名 | 包 | 动作 | 职责 |
|------|---|------|------|
| `ToolAutoRegistrar` | tool | **改** | 注册时检查 `ConditionalTool.isAvailable()`，不可用的不注册 |
| `FileTools` | tool.builtin | **改** | 三个内部类各注入 Sandbox，execute() 第一行 enforce() |
| `ShellTools` | tool.builtin | **改** | 注入 Sandbox，execute() 第一行 enforce() |
| `HttpTools` | tool.builtin | **改** | 两个内部类各注入 Sandbox，execute() 第一行 enforce() |
| `NotifyTool` | tool.notify | **新增** | 独立 `@Component`，实现 `OryxTool` + `ConditionalTool` |
| `NotifyTools` | tool.notify | **删除** | 职责已拆分到 NotifyTool |
| `Tool` | tool.annotation | **新增** | `@Target(METHOD)` 注解，声明工具名、描述 |
| `ToolMethodScanner` | tool.scanner | **新增** | 扫描所有 `@Tool` 方法，包装为 `AnnotatedToolAdapter` 注册 |
| `AnnotatedToolAdapter` | tool.scanner | **新增** | 将 `@Tool` 方法适配为 `OryxTool` 接口 |

### 类关系

```
OryxTool (interface)
  ├── FileTools.ReadFileTool     (implements, @Component)
  ├── FileTools.WriteFileTool    (implements, @Component)
  ├── FileTools.ListDirTool      (implements, @Component)
  ├── ShellTools.ShellTool       (implements, @Component)
  ├── HttpTools.HttpGetTool      (implements, @Component)
  ├── HttpTools.HttpPostTool     (implements, @Component)
  ├── NotifyTool                 (implements + ConditionalTool, @Component)
  ├── McpToolAdapter             (implements, 动态创建)
  └── AnnotatedToolAdapter       (implements, 扫描创建)

ConditionalTool (interface)
  └── NotifyTool                 (implements)

ToolAutoRegistrar ──→ ToolRegistry ←── McpLoader
                         ↑
                    ToolMethodScanner
```

### 关键方法伪代码

#### ToolExecutor.execute()（重构后）

```java
public ToolResult execute(Session session, ToolCallRequest request, List<OryxTool> tools) {
    String toolName = request.getToolName();
    Map<String, Object> input = request.getArguments();

    // 1. 查找工具（不再做 Sandbox 分发）
    OryxTool tool = tools.stream()
        .filter(t -> t.getName().equals(toolName))
        .findFirst().orElse(null);
    if (tool == null) {
        return ToolResult.fail("Tool not found: " + toolName, false);
    }

    // 2. 执行（工具内部自己调 Sandbox）
    long start = System.currentTimeMillis();
    ToolResult result;
    try {
        result = tool.execute(input);
    } catch (Exception e) {
        log.error("Tool {} execution failed: {}", toolName, e.getMessage());
        result = ToolResult.fail("Execution error: " + e.getMessage(), true);
    }
    long duration = System.currentTimeMillis() - start;

    // 3. 审计
    try {
        String inputJson = mapToJson(input);
        String resultJson = result.isSuccess() ? result.getContent() : result.getError();
        auditPort.recordToolInvocation(session.getSessionId(), toolName,
            inputJson, resultJson, result.isSuccess(),
            result.isSuccess() ? null : result.getError(), duration);
    } catch (Exception e) {
        log.warn("Failed to record tool audit: {}", e.getMessage());
    }

    return result;
}
```

#### ReActLoop 工具执行循环（重构后）

```java
// 执行每个工具调用（带隔离防护）
for (ToolCallRequest tc : toolCalls) {
    try {
        ToolResult result = toolExecutor.execute(session, tc, tools);
        String resultContent;
        if (result.isSuccess()) {
            resultContent = result.getContent();
        } else {
            resultContent = "Error: " + result.getError()
                + (result.isRetryable() ? " [retryable]" : " [not retryable]");
        }
        session.addMessage(new Message("tool", "[" + tc.getToolName() + "] " + resultContent));
    } catch (Exception e) {
        // 防御性兜底：即使 ToolExecutor 漏了异常，也不影响同批次其他工具
        log.error("Tool {} unexpected failure: {}", tc.getToolName(), e.getMessage());
        session.addMessage(new Message("tool",
            "[" + tc.getToolName() + "] Error: " + e.getMessage()));
    }
}
```

#### FileTools.ReadFileTool（Sandbox 下沉示例）

```java
@Component("readFileTool")
public static class ReadFileTool implements OryxTool {
    private final Sandbox sandbox;

    public ReadFileTool(@Autowired(required = false) Sandbox sandbox) {
        this.sandbox = sandbox;
    }

    @Override
    public ToolResult execute(Map<String, Object> input) {
        // 第一行：Sandbox 校验
        String path = (String) input.get("path");
        if (sandbox != null) {
            sandbox.enforce(new SandboxAction(SandboxAction.ActionType.FILE_READ, path));
        }
        // 后续：正常 IO
        if (path == null || path.isBlank()) {
            return ToolResult.fail("path parameter is required", false);
        }
        try {
            Path p = Path.of(path);
            if (!Files.exists(p)) return ToolResult.fail("File not found: " + path, false);
            String content = Files.readString(p);
            return ToolResult.ok(content);
        } catch (IOException e) {
            return ToolResult.fail("Failed to read file: " + e.getMessage(), true);
        }
    }
}
```

#### ToolAutoRegistrar（条件注册）

```java
@Override
public void run(ApplicationArguments args) {
    List<OryxTool> available = new ArrayList<>();
    int skipped = 0;
    for (OryxTool tool : tools) {
        if (tool instanceof ConditionalTool ct && !ct.isAvailable()) {
            log.info("Skipping unavailable tool: {}", tool.getName());
            skipped++;
        } else {
            available.add(tool);
        }
    }
    toolRegistry.registerAll(available);
    log.info("Auto-registered {} tools ({} skipped): {}",
        available.size(), skipped,
        available.stream().map(OryxTool::getName).toList());
}
```

### 数据流：一次工具调用的完整路径

```
LLM 返回 tool_call
    │
    ▼
Provider 层解析 → ToolCallRequest(name, arguments)
    │
    ▼
ReActLoop → ToolExecutor.execute(session, request, tools)
    │
    ├── 查找：tools.stream().filter(name match)
    │
    ├── 执行：tool.execute(arguments)
    │     └── 工具内部：sandbox.enforce() → 实际 IO → 返回 ToolResult
    │
    ├── 兜底：catch Exception → ToolResult.fail
    │
    └── 审计：auditPort.recordToolInvocation(...)
    │
    ▼
ReActLoop 拼接结果（含 [retryable] 信号）→ 写入对话历史
    │
    ▼
下一轮 ReAct 迭代（模型看到结果，决定下一步）
```

---

## 第四段：验收 harness：把验收标准变成可执行的测试

### 测试分层

| 层级 | 覆盖目标 | 测试类 |
|------|---------|--------|
| 单元测试 | 每个组件独立验证 | 12 个测试类，51 个用例 |
| InOrder 测试 | 钉死 Sandbox 校验顺序 | BuiltInToolsSandboxOrderTest |
| 端到端验收 | 零/轻/重代码三条路径 | 手工执行 |

### 单元测试用例

#### 一、Sandbox 下沉

**FileToolsSandboxTest**（5 用例）

| 用例名 | 输入 | 期望输出 | 验证点 |
|--------|------|---------|--------|
| `readFile_sandboxCalledFirst` | mock Sandbox + 白名单内 path | ToolResult.ok，enforce(FILE_READ) 被调用 | Sandbox 在 IO 之前 |
| `readFile_sandboxRejectBlocksIO` | mock Sandbox 抛 SecurityException | 抛 SecurityException，不读文件 | verify: Files.readString 未被调用 |
| `writeFile_sandboxEnforceFileWrite` | mock Sandbox + 白名单内 path | ToolResult.ok，enforce(FILE_WRITE) 被调用 | ActionType 是 FILE_WRITE |
| `listDir_sandboxEnforceFileRead` | mock Sandbox + 白名单内 path | ToolResult.ok，enforce(FILE_READ) 被调用 | list_dir 用 FILE_READ |
| `readFile_noSandboxStillWorks` | Sandbox 为 null | ToolResult.ok，正常读文件 | Sandbox 可选依赖 |

**测试数据准备**：mock `Sandbox`（控制通过/拒绝）+ 临时目录真实文件。

**ShellToolsSandboxTest**（3 用例）

| 用例名 | 输入 | 期望输出 | 验证点 |
|--------|------|---------|--------|
| `shell_sandboxCalledFirst` | mock Sandbox + command="echo" | ToolResult.ok，enforce(SHELL_COMMAND) 被调用 | Sandbox 在 ProcessBuilder 之前 |
| `shell_sandboxRejectBlocksExecution` | mock Sandbox 抛 SecurityException | 抛 SecurityException | verify: ProcessBuilder.start() 未被调用 |
| `shell_noSandboxStillWorks` | Sandbox 为 null | ToolResult.ok | Sandbox 可选依赖 |

**测试数据准备**：mock `Sandbox` + 执行简单命令（如 `echo hello`）。

**HttpToolsSandboxTest**（4 用例）

| 用例名 | 输入 | 期望输出 | 验证点 |
|--------|------|---------|--------|
| `httpGet_sandboxCalledFirst` | mock Sandbox + 白名单域名 | ToolResult.ok，enforce(HTTP_REQUEST) 被调用 | Sandbox 在 HTTP 请求之前 |
| `httpGet_sandboxRejectBlocksRequest` | mock Sandbox 抛 SecurityException | 抛 SecurityException | 不发 HTTP 请求 |
| `httpPost_sandboxEnforceHttpRequest` | mock Sandbox + 白名单域名 | ToolResult.ok，enforce(HTTP_REQUEST) 被调用 | http_post 同样校验 |
| `httpGet_noSandboxStillWorks` | Sandbox 为 null | ToolResult.ok | Sandbox 可选依赖 |

**测试数据准备**：mock `Sandbox` + JDK HttpServer mock 服务端。

#### 二、条件注册

**ConditionalToolTest**（3 用例）

| 用例名 | 输入 | 期望输出 | 验证点 |
|--------|------|---------|--------|
| `availableTool_getsRegistered` | isAvailable()=true | registry.getTool(name) != null | 注册成功 |
| `unavailableTool_notRegistered` | isAvailable()=false | registry.getTool(name) == null | 不注册 |
| `nonConditionalTool_alwaysRegistered` | 普通 OryxTool（不实现 ConditionalTool） | registry.getTool(name) != null | 不影响非 ConditionalTool |

**测试数据准备**：手写 mock OryxTool + ConditionalTool 实现。

**ToolAutoRegistrarTest**（2 用例）

| 用例名 | 输入 | 期望输出 | 验证点 |
|--------|------|---------|--------|
| `registerAllConditionalTools` | 1 available + 1 unavailable + 1 普通 | 2 个注册，1 个跳过 | unavailable 被跳过 |
| `registerLogsSkippedTools` | 含 unavailable 工具 | 日志含 "Skipped" | 可观测性 |

**测试数据准备**：构造 ToolRegistry + mock OryxTool 列表。

#### 三、ToolExecutor 重构

**ToolExecutorTest**（10 用例）

| 用例名 | 输入 | 期望输出 | 验证点 |
|--------|------|---------|--------|
| `execute_toolNotFound` | toolName="nonexistent" | ToolResult.fail(retryable=false) | 不执行任何工具 |
| `execute_success` | 工具存在，execute 返回 ok | ToolResult.ok，审计 success=true | 查找 → 执行 → 审计 |
| `execute_exceptionCaught` | 工具 execute 抛 RuntimeException | ToolResult.fail(retryable=true) | 兜底异常，不向上抛 |
| `execute_failureWithRetryableTrue` | 工具返回 fail(retryable=true) | 结果含 retryable=true | 信号正确传递 |
| `execute_failureWithRetryableFalse` | 工具返回 fail(retryable=false) | 结果含 retryable=false | 信号正确传递 |
| `execute_noSandboxCheck` | 任意工具调用 | Sandbox.enforce 不被 ToolExecutor 调用 | performSandboxCheck 已删除 |
| `execute_auditRecorded` | 任意工具调用 | auditPort.recordToolInvocation 被调用 | 审计落库 |
| `execute_toolThrowsRuntimeException` | 工具抛 RuntimeException | ToolResult.fail | 不拖垮流程 |
| `execute_toolThrowsError` | 工具抛 Error | ToolResult.fail | 连 Error 也兜住 |
| `execute_toolReturnsNull` | 工具返回 null | 不抛 NullPointerException | 防御 null |

**测试数据准备**：mock OryxTool + mock AuditPort + mock Session。

#### 四、NotifyTool 独立

**NotifyToolTest**（6 用例）

| 用例名 | 输入 | 期望输出 | 验证点 |
|--------|------|---------|--------|
| `execute_channelFound_success` | channel="team-lark"，Repository 返回渠道 | ToolResult.ok | 查渠道 → 构建 target → 发送 |
| `execute_channelNotFound` | channel="nonexistent" | ToolResult.fail | 渠道不存在 |
| `execute_defaultChannelFallback` | 不传 channel，Profile 配了默认 | 使用默认渠道 | fallback 逻辑 |
| `isAvailable_channelsExist` | Repository 返回非空 | isAvailable()=true | 条件注册 |
| `isAvailable_noChannels` | Repository 返回空 | isAvailable()=false | 渠道表空不注册 |
| `execute_contentBlank` | content="" | ToolResult.fail | 参数校验 |

**测试数据准备**：mock NotifyChannelRepository + mock NotifyChannelAdapter。

#### 五、@Tool 注解体系

**ToolMethodScannerTest**（3 用例）

| 用例名 | 输入 | 期望输出 | 验证点 |
|--------|------|---------|--------|
| `scan_findsAnnotatedMethods` | Spring 容器含 @Tool Bean | 扫描出所有 @Tool 方法 | 不遗漏 |
| `scan_registersAsAnnotatedToolAdapter` | 扫描结果 | 每个 @Tool 方法包装为 AnnotatedToolAdapter | 适配器包装正确 |
| `scan_skipsNonAnnotatedMethods` | Bean 含普通方法 | 普通方法不被扫描 | 不误注册 |

**测试数据准备**：测试用 @Component 含 @Tool 方法 + Spring 测试容器。

**AnnotatedToolAdapterTest**（5 用例）

| 用例名 | 输入 | 期望输出 | 验证点 |
|--------|------|---------|--------|
| `getName_returnsAnnotatedName` | @Tool(name="my_tool") | getName()="my_tool" | 名称映射 |
| `getDescription_returnsAnnotatedDesc` | @Tool(description="...") | getDescription() 返回描述 | 描述映射 |
| `getInputSchema_generatedFromParams` | 方法参数 (String url, int count) | Schema 含 url、count 字段 | Schema 生成 |
| `execute_invokesOriginalMethod` | execute(input) | 原方法被调用，参数正确传递 | 反射调用 |
| `execute_exceptionReturnsFail` | 原方法抛异常 | ToolResult.fail | 兜底异常 |

**测试数据准备**：手写 @Tool 测试方法 + mock 调用。

#### 六、ReActLoop 联动

**ReActLoopRetryableTest**（3 用例）

| 用例名 | 输入 | 期望输出 | 验证点 |
|--------|------|---------|--------|
| `toolFailure_retryableSignalInHistory` | 工具返回 fail(retryable=true) | 对话历史含 "[retryable]" | 模型能看到信号 |
| `toolFailure_notRetryableSignalInHistory` | 工具返回 fail(retryable=false) | 对话历史含 "[not retryable]" | 模型能看到信号 |
| `toolFailure_loopContinues` | 工具失败 | ReAct 循环不中断，继续下一轮 | 循环不断 |

**ReActLoopIsolationTest**（4 用例）

| 用例名 | 输入 | 期望输出 | 验证点 |
|--------|------|---------|--------|
| `oneToolFails_otherToolsStillExecute` | 同一轮 2 个 tool_call，第 1 个失败 | 第 2 个工具被执行，结果写入历史 | 同批次内隔离 |
| `oneToolFails_loopContinues` | 第 N 轮工具失败 | 第 N+1 轮正常继续 | 跨轮次隔离 |
| `oneToolFails_doesNotAffectSessionHistory` | 工具失败 | 之前的消息不被篡改 | 历史完整性 |
| `allToolsFail_loopTerminatesGracefully` | 所有工具都失败 | 达到 maxIterations 后正常返回 | 优雅终止 |

**测试数据准备**：mock LLM + mock ToolExecutor + mock AuditPort。

#### 七、InOrder 顺序

**BuiltInToolsSandboxOrderTest**（3 用例）

| 用例名 | 验证点 |
|--------|--------|
| `readFile_sandboxBeforeIO` | InOrder: sandbox.enforce(FILE_READ) → Files.readString |
| `shell_sandboxBeforeProcess` | InOrder: sandbox.enforce(SHELL_COMMAND) → ProcessBuilder.start |
| `httpGet_sandboxBeforeRequest` | InOrder: sandbox.enforce(HTTP_REQUEST) → HttpClient.send |

### 测试统计

| 测试类 | 用例数 | 覆盖的改动 |
|--------|--------|-----------|
| FileToolsSandboxTest | 5 | Sandbox 下沉 |
| ShellToolsSandboxTest | 3 | Sandbox 下沉 |
| HttpToolsSandboxTest | 4 | Sandbox 下沉 |
| ConditionalToolTest | 3 | 条件注册接口 |
| ToolAutoRegistrarTest | 2 | 条件注册逻辑 |
| ToolExecutorTest | 10 | 删除 Sandbox + 隔离 |
| NotifyToolTest | 6 | 独立组件 |
| ToolMethodScannerTest | 3 | @Tool 扫描 |
| AnnotatedToolAdapterTest | 5 | @Tool 适配 |
| ReActLoopRetryableTest | 3 | retryable 信号传递 |
| ReActLoopIsolationTest | 4 | 失败隔离 |
| BuiltInToolsSandboxOrderTest | 3 | InOrder 顺序 |
| **合计** | **51** | |

### 运行命令

```bash
# Sandbox 下沉
mvn test -pl oryxos-tool "-Dtest=FileToolsSandboxTest,ShellToolsSandboxTest,HttpToolsSandboxTest"

# 条件注册
mvn test -pl oryxos-core "-Dtest=ConditionalToolTest,ToolAutoRegistrarTest"

# ToolExecutor 重构
mvn test -pl oryxos-core "-Dtest=ToolExecutorTest"

# NotifyTool 独立
mvn test -pl oryxos-tool "-Dtest=NotifyToolTest"

# @Tool 注解
mvn test -pl oryxos-tool "-Dtest=ToolMethodScannerTest,AnnotatedToolAdapterTest"

# ReActLoop 联动
mvn test -pl oryxos-core "-Dtest=ReActLoopRetryableTest,ReActLoopIsolationTest"

# InOrder 顺序
mvn test -pl oryxos-tool "-Dtest=BuiltInToolsSandboxOrderTest"

# 全量回归
mvn clean verify
```

---

## 第五段：做完怎样验

### 端到端验收场景

| # | 验收项 | 操作 | 预期结果 | 类型 |
|---|--------|------|---------|------|
| 1 | 零代码 MCP | 写 AGENT.md 配 `mcp_servers` 指向现成 MCP Server → 启动 → `oryxos tool list` | 看到 MCP 工具，能调用并返回结果 | blocking |
| 2 | 轻代码 MCP | 用任意语言写简单 MCP Server → AGENT.md 配置连接 → 启动 → `oryxos tool list` | 看到自定义 MCP 工具，能调用并返回结果 | blocking |
| 3 | 全流程 ReAct | 调真实 MCP + SKILL.md → Agent 跑完完整 ReAct 循环 | 模型选工具 → 工具执行 → 结果回填 → 模型输出最终回答 | blocking |
| 4 | 重代码 @Tool | 写 @Tool 注解 Java 方法 → 启动 → `oryxos tool list` | 看到自定义 Java 工具，能调用并返回结果 | blocking |
| 5 | 全量测试 | `mvn test` | 全绿，0 failures | blocking |
| 6 | 审计完整 | 执行以上所有工具调用后查 `tool_invocations` 表 | 每次调用都有记录（含成功/失败） | blocking |

### 人工确认项

| # | 验收项 | 操作 | 预期结果 | 类型 |
|---|--------|------|---------|------|
| 1 | Sandbox 自治 | 检查 FileTools / ShellTools / HttpTools 代码 | execute() 第一行调 sandbox.enforce() | blocking |
| 2 | ToolExecutor 纯净 | 检查 ToolExecutor 代码 | 无 performSandboxCheck 方法 | blocking |
| 3 | NotifyTool 条件注册 | 渠道表空 → 启动 | NotifyTool 不注册，LLM 看不到 | blocking |
| 4 | @Tool 扫描 | 写 @Tool 方法 → 启动 | 自动注册到 ToolRegistry | blocking |
| 5 | 回归不破 | `mvn clean verify` | BUILD SUCCESS，0 failures | blocking |
| 6 | 前序天回归 | Day 1-4 测试全绿 | 26 个 Notify 测试 + 前序测试不受影响 | blocking |
| 7 | 失败隔离 | 模拟工具抛异常 | 同批次其他工具正常执行，循环不中断 | blocking |

### 自动化验收标准

```bash
# 1. 编译打包
mvn clean package

# 2. 单元测试全绿
mvn test

# 3. 完整验证（含 Spotless / Checkstyle / SpotBugs）
mvn clean verify

# 期望：BUILD SUCCESS，0 failures, 0 errors
```

### 与 agents.md 原则对齐

| 原则 | 对齐情况 |
|------|---------|
| H1: 代码全英文，注释用中文 | ✅ 遵守 |
| H2: 传统 switch-case | ✅ 无增强 switch |
| H3: 凭证走环境变量 | ✅ 不涉及 |
| H4: 不用 ChatClient/Advisor | ✅ 只改 Tool 层 |
| H5: 测试方法名英文，@DisplayName 中文 | ✅ 遵守 |
| H6: mvn clean verify 全绿 | ✅ 目标 |
| 工具自治 | ✅ 每个工具自己管 Sandbox |
| 不静默失败 | ✅ 异常兜底成 ToolResult.fail |
| 失败不拖垮流程 | ✅ 防御性 try-catch |

### 性能 / 安全 / 可观测性

| 维度 | 验收点 | 类型 |
|------|--------|------|
| 安全 | 每个内置工具 execute() 第一行 Sandbox 校验 | blocking |
| 安全 | MCP 工具核心阶段不做 Sandbox（已知限制） | non-blocking |
| 安全 | @Tool 工具自己负责 Sandbox | blocking |
| 可观测性 | ToolAutoRegistrar 日志含注册数量和跳过数量 | non-blocking |
| 可观测性 | 审计记录 tool_invocations 含成功/失败 | blocking |
| 性能 | 工具执行超时不阻塞 ReAct 循环 | blocking |

### 交付物统计

| 类别 | 数量 |
|------|------|
| 生产代码 | 新增 6 + 改 5 + 删 1 = 12 个文件 |
| 测试代码 | 新增 12 个测试类，51 个用例 |
| **合计** | **24 个文件** |

---

## 变更记录

| 日期 | 变更内容 | 变更人 |
|------|---------|--------|
| 2026-10-23 | 初始版本：Tool 模块重构设计文档 | AI |
