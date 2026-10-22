# CLI 模块设计文档

**模块名称**：CLI（Channel CLI）  
**文档版本**：v1.0  
**创建日期**：2026-10-20  
**对应课件**：Day 3

---

## 第一段：模块是什么，干嘛用的

### 模块定位

CLI 是 OryxOS 的**消息进出之门**——用户通过命令行与 Agent 交互的唯一入口。它不处理业务逻辑，只负责读输入、调服务、格式化输出。

### 解决什么问题

1. **本地交互**：用户在终端里直接跟 Agent 对话（`oryxos chat`）
2. **服务启动**：把 OryxOS 跑成 Web 服务或守护进程（`oryxos serve` / `gateway`）
3. **状态查看**：快速了解系统里有什么 Agent、Provider、Tool（`status`、`profile list` 等）
4. **工作区管理**：初始化项目、创建/删除 Agent Profile（`init`、`profile create/delete`）

### 上下游关系

```
用户 ─→ CLI（读 stdin）
         │
         ├─ 轻命令：直接执行（文件 I/O）
         │
         └─ 重命令：启动 Spring → AgentService → ReActLoop → ProviderService → LLM
                     │
                     └─ 打印响应（stdout）
```

**上游**：用户（终端输入）  
**下游**：AgentService（重命令）、文件系统（轻命令）

### 核心职责（5 条）

1. 命令解析与路由（Picocli）
2. 交互式对话循环（chat）
3. 服务进程管理（serve/gateway）
4. 文件操作（profile CRUD、init）
5. 状态查询与格式化输出

---

## 第二段：动手之前先想清楚几件事

### 设计决策

| 决策 | 选择 | 为什么 |
|------|------|--------|
| 命令解析工具 | Picocli 4.7.6 | 项目已集成，支持子命令、Spring 集成、自动帮助 |
| 轻重命令分离 | 轻命令不启动 Spring | 毫秒级响应，避免 Spring 启动开销 |
| Session 持久化 | 本次不做，内存版 | 核心阶段聚焦"门"本身，持久化留到后续 |
| 扫描范围 | 显式声明包名 | 不扫全量 `com.oryxos`，新增模块需显式加入 |

### 关键约束

- **CLI 是展示层**：不碰业务逻辑、数据持久化、LLM 调用
- **轻命令零 Spring 依赖**：`init`、`profile *`、`provider list`、`tool list`、`status` 直接执行
- **重命令统一启动验证**：Bean 断言 + 数据访问层读写验证，不区分命令类型
- **代码全英文，注释用中文**

### 避坑指南

| 陷阱 | 应对 |
|------|------|
| Picocli + Spring Boot 的 `System.exit()` 陷阱 | 重命令检测 `serve`/`gateway`，跳过 `System.exit()` |
| `scanner.close()` 关闭 System.in | 不关闭 Scanner，JVM 退出时自动清理 |
| 轻命令依赖 Spring Bean | 轻命令直接读文件/轻量数据，不注入 Bean |

### 边界划分

**CLI 负责**：
- 命令解析、参数校验
- 读 stdin、写 stdout/stderr
- 调用 AgentService（重命令）
- 文件操作（轻命令）
- 输出格式化

**CLI 不负责**：
- 业务逻辑（ReActLoop、PromptBuilder）
- 数据持久化（Repository、JPA）
- LLM 调用（ProviderService、ChatModel）
- Tool 执行（ToolExecutor、OryxTool）
- Memory 管理（MemoryService）

---

## 第三段：代码怎么写

### 核心类清单

| 类名 | 职责 | 类型 |
|------|------|------|
| `OryxOsCli` | 主入口，轻重命令路由 | 重 |
| `ChatCommand` | 交互式多轮对话 | 重 |
| `ServeCommand` | 启动 Web 服务 | 重 |
| `GatewayCommand` | 守护进程，挂载多通道 | 重 |
| `InitCommand` | 初始化工作区 | 轻 |
| `StatusCommand` | 系统状态总览 | 轻 |
| `ProfileCommand` | profile 命令组 | 轻 |
| `ProfileListCommand` | 列出所有 Profile | 轻 |
| `ProfileCreateCommand` | 创建新 Profile | 轻 |
| `ProfileShowCommand` | 查看 Profile 详情 | 轻 |
| `ProfileDeleteCommand` | 删除 Profile | 轻 |
| `ProviderCommand` | provider list | 轻 |
| `ToolCommand` | tool list | 轻 |
| `WorkspacePaths` | `.oryxos/` 目录常量 | 工具类 |

### 关键方法伪代码

#### OryxOsCli.main() — 轻重路由

```java
public static void main(String[] args) {
    if (args.length == 0 || !LIGHT_COMMANDS.contains(args[0])) {
        runHeavyCommand(args);  // 启动 Spring
    } else {
        runLightCommand(args);  // 直接执行
    }
}
```

#### ChatCommand.run() — 核心循环

```java
public void run() {
    // 1. 加载 Agent
    agentLoader.loadAgents(WorkspacePaths.workDir());
    
    // 2. 选择 Profile
    Profile profile = resolveProfile();
    
    // 3. 创建 Session
    Session session = sessionManager.getOrCreateSession("cli", "cli-user", profile.getName());
    
    // 4. 核心循环
    Scanner scanner = new Scanner(System.in);
    while (scanner.hasNextLine()) {
        System.out.print("> ");
        String input = scanner.nextLine().trim();
        
        if (input.isEmpty()) continue;
        if ("/quit".equals(input)) break;
        
        try {
            String reply = agentService.process(session, input);
            System.out.println(reply);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
```

#### ProfileCreateCommand.run() — 生成模板

```java
public void run() {
    Path agentDir = WorkspacePaths.agentsDir().resolve(name);
    Path agentMd = agentDir.resolve("AGENT.md");
    
    if (Files.exists(agentMd)) {
        System.err.println("Profile already exists: " + name);
        return;
    }
    
    Files.createDirectories(agentDir);
    Files.writeString(agentMd, TEMPLATE.formatted(name, name));
    System.out.println("Created profile: " + name);
}
```

### 配置结构

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `scanBasePackages` | String[] | `{core, provider, storage, cli}` | 重命令扫描范围 |
| `LIGHT_COMMANDS` | Set<String> | `{init, status, profile, provider, tool}` | 轻命令集合 |

### 数据流走向

```
轻命令：用户输入 → Picocli 解析 → 直接执行 → 文件 I/O → 格式化输出
重命令：用户输入 → Picocli 解析 → Spring 启动 → Bean 断言 → 读写验证 → AgentService.process() → 打印响应
```

### 异常处理策略

| 异常场景 | 处理方式 |
|----------|---------|
| Agent 不存在 | stderr: "No agents found. Run 'oryxos init' first." |
| Profile 已存在 | stderr: "Profile already exists: {name}" |
| AgentService 抛异常 | stderr: "Error: {message}"，继续循环 |
| 文件 I/O 失败 | stderr: "Failed to {action}: {message}" |

---

## 第四段：验收 harness：把验收标准变成可执行的测试

### 测试分层

| 层级 | 测试类 | 数量 | 说明 |
|------|--------|------|------|
| 单元测试 | ProfileCommandTest | 4 | profile CRUD |
| 单元测试 | ProviderCommandTest | 2 | provider list |
| 单元测试 | ToolCommandTest | 2 | tool list |
| 单元测试 | StatusCommandTest | 1 | status 输出 |
| 单元测试 | InitCommandTest | 2 | 幂等性 |
| 单元测试 | ChatCommandTest | 4 | 核心循环 |
| 单元测试 | SessionManagerTest | 2 | Session 幂等 |
| 集成测试 | SessionRepositoryTest | 1 | messages_json 持久化 |
| **合计** | **8 个测试类** | **18** | |

### 关键测试用例

| 用例名 | 输入 | 期望输出 | 验证点 |
|--------|------|---------|--------|
| `list_emptyDirectory` | `.oryxos/agents/` 为空 | "No agents found" | 轻命令不启动 Spring |
| `create_and_list` | `profile create test` | list 能看到 test | 文件创建成功 |
| `quit_exitsLoop` | `/quit` | 循环结束 | 退出逻辑正确 |
| `emptyInput_skipped` | 空输入 | 不触发 process() | 空输入跳过 |
| `getOrCreate_idempotent` | 同一三元组两次调用 | 返回同一 Session | 幂等性 |
| `messagesJson_survivesRestart` | 写入后新建 Context 重查 | messages_json 还在 | 持久化完整 |

### 测试数据准备

- **轻命令**：使用 `@TempDir` 创建临时目录，模拟 `.oryxos/` 结构
- **重命令**：Mock AgentService、SessionManager 等 Bean
- **集成测试**：使用 `@Tag("integration")`，CI 跳过

### 运行命令

```bash
# 单元测试（默认）
mvn test

# 包含集成测试
mvn test -Dgroups=integration

# 完整验证
mvn clean verify
```

---

## 第五段：做完怎样验

### 人工确认项

| # | 验收项 | 操作 | 预期结果 | 类型 |
|---|--------|------|---------|------|
| 1 | 轻命令不启动 Spring | `time oryxos init` | 毫秒级返回，无 Spring 日志 | blocking |
| 2 | 轻命令不启动 Spring | `time oryxos profile list` | 毫秒级返回，无 Spring 日志 | blocking |
| 3 | profile create 生成模板 | `oryxos profile create test-agent` | AGENT.md 存在，frontmatter 正确 | blocking |
| 4 | profile 幂等性 | 再次 `profile create test-agent` | 提示 "already exists" | blocking |
| 5 | profile delete | `profile delete test-agent` | 目录删除，list 看不到 | blocking |
| 6 | chat 核心循环 | `oryxos chat weather` → 消息 → `/quit` | 能对话，/quit 退出 | blocking |
| 7 | chat 空输入跳过 | `oryxos chat` → 直接回车 | 不报错，重新显示 `>` | blocking |
| 8 | chat 异常友好提示 | 配错 API Key → `oryxos chat` | 打印 "Error: ..."，不抛堆栈 | blocking |
| 9 | serve 启动 Web | `oryxos serve` → `curl localhost:8080/api/v1/health` | 返回 UP | blocking |
| 10 | gateway 守护进程 | `oryxos gateway` | 进程常驻，不退出 | blocking |

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
| H3: 凭证走环境变量 | ✅ 无明文 |
| H4: 不用 ChatClient/Advisor | ✅ 只调 AgentService |
| H5: 测试方法名英文，@DisplayName 中文 | ✅ 遵守 |
| H6: mvn clean verify 全绿 | ✅ 目标 |

### 性能/安全/可观测性

| 维度 | 验收点 | 类型 |
|------|--------|------|
| 性能 | 轻命令响应 < 100ms | non-blocking |
| 安全 | 无明文 API Key | blocking |
| 可观测性 | 异常日志含上下文 | non-blocking |

---

## 变更记录

| 日期 | 变更内容 | 变更人 |
|------|---------|--------|
| 2026-10-20 | 初始版本：CLI 模块设计文档 | AI |
