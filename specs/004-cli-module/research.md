# Research: CLI Module

**Feature**: 004-cli-module  
**Date**: 2026-10-20  
**Status**: Complete

---

## R1: 轻重命令路由策略

**Decision**: 在 `OryxOsCli.main()` 中根据命令名白名单判断轻重，轻命令直接 `new CommandLine(lightCmd).execute()`，重命令走 `SpringApplication.run()`。

**Rationale**: 
- 白名单方式简单直观，不需要注解或反射
- 轻命令不加载 Spring Context，启动时间 < 50ms
- 重命令统一走 Spring Boot 启动流程

**Alternatives considered**:
- ❌ 注解标记 `@LightCommand` / `@HeavyCommand`：增加复杂度，核心阶段不需要
- ❌ 配置文件声明：过度设计，白名单硬编码即可

**实现要点**:
```java
private static final Set<String> LIGHT_COMMANDS = Set.of(
    "init", "status", "profile", "provider", "tool"
);

public static void main(String[] args) {
    if (args.length == 0 || !LIGHT_COMMANDS.contains(args[0])) {
        runHeavyCommand(args);  // 启动 Spring
        return;
    }
    runLightCommand(args);  // 不启动 Spring
}
```

---

## R2: 重命令启动验证机制

**Decision**: 统一在 `StartupVerifier.verify()` 中做三步检查：
1. 显式 `scanBasePackages` 声明（编译期保证）
2. Bean 断言（`ApplicationContext.getBean()` 关键 Bean）
3. 数据访问层读写验证（`SessionRepository.count()`）

**Rationale**:
- 三步验证覆盖装配完整性，避免运行时才发现 Bean 缺失
- 所有重命令统一调用 `verify()`，不重复代码
- 数据访问层读写验证保证 SQLite 连接正常

**Alternatives considered**:
- ❌ 只验证 Bean 存在：不够，可能 Bean 存在但依赖注入失败
- ❌ 每个命令自己验证：重复代码，维护成本高

**实现要点**:
```java
@Component
public class StartupVerifier {
    @Autowired
    private SessionRepository sessionRepository;
    
    public void verify() {
        // Step 1: scanBasePackages 已在 @SpringBootApplication 声明（编译期）
        // Step 2: Bean 断言
        ApplicationContext ctx = SpringContextHolder.getContext();
        ctx.getBean(AgentService.class);  // 缺失会抛异常
        ctx.getBean(SessionManager.class);
        // Step 3: 数据访问层读写
        sessionRepository.count();  // 验证 SQLite 连接
    }
}
```

---

## R3: Picocli 命令分组与子命令

**Decision**: `profile`、`provider`、`tool` 用 Picocli 的 `@Command(subcommands = {...})` 声明子命令组，子命令类独立。

**Rationale**:
- Picocli 原生支持命令分组，符合用户直觉（`profile list`、`profile create`）
- 子命令独立成类，职责清晰，测试方便

**Alternatives considered**:
- ❌ 所有命令平铺（`profile-list`、`profile-create`）：不符合用户习惯
- ❌ 动态注册子命令：过度设计

**实现要点**:
```java
@Command(name = "profile", subcommands = {
    ProfileListCommand.class,
    ProfileCreateCommand.class,
    ProfileShowCommand.class,
    ProfileDeleteCommand.class
})
public class ProfileCommand implements Runnable {
    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
}

@Command(name = "list")
public class ProfileListCommand implements Runnable {
    @Override
    public void run() {
        // 扫描 .oryxos/agents/ 目录
    }
}
```

---

## R4: SessionManager 内存版实现

**Decision**: `SessionManager` 用 `ConcurrentHashMap<String, Session>` 存储，`generateSessionId()` 用 SHA-256(channel + ":" + userId + ":" + profileName) 取 64 字符。

**Rationale**:
- 核心阶段不持久化，重启后会话丢失可接受
- ConcurrentHashMap 线程安全，支持并发访问
- SHA-256 保证三元组唯一性

**Alternatives considered**:
- ❌ 直接写 SQLite：核心阶段不需要，增加复杂度
- ❌ UUID 生成 session_id：无法从三元组反推，调试困难

**实现要点**:
```java
@Component
public class SessionManager {
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    
    public Session getOrCreateSession(String channel, String userId, String profileName) {
        String sessionId = generateSessionId(channel, userId, profileName);
        return sessions.computeIfAbsent(sessionId, id -> {
            Session s = new Session();
            s.setSessionId(id);
            s.setChannel(channel);
            s.setUserId(userId);
            s.setProfileName(profileName);
            s.setMessages(new ArrayList<>());
            s.setStatus("active");
            return s;
        });
    }
    
    private String generateSessionId(String channel, String userId, String profileName) {
        String input = channel + ":" + userId + ":" + profileName;
        return DigestUtils.sha256Hex(input).substring(0, 64);
    }
}
```

---

## R5: 轻命令文件操作策略

**Decision**: `profile list/create/show/delete` 直接操作 `.oryxos/agents/` 目录，用 `java.nio.file.Files` API，不经过 Spring Bean。

**Rationale**:
- 轻命令不启动 Spring，无法注入 Bean
- 文件操作是 Profile 管理的核心，直接读写 AGENT.md 即可
- 避免引入额外的文件抽象层

**Alternatives considered**:
- ❌ 启动轻量 Spring Context：违背"轻命令不启动 Spring"原则
- ❌ 用 ProfileRegistry（Spring Bean）：轻命令无法访问

**实现要点**:
```java
@Command(name = "list")
public class ProfileListCommand implements Runnable {
    @Override
    public void run() {
        Path agentsDir = Path.of(System.getProperty("user.dir"), ".oryxos", "agents");
        if (!Files.exists(agentsDir)) {
            System.out.println("No agents found. Run 'oryxos init' first.");
            return;
        }
        try (Stream<Path> dirs = Files.list(agentsDir)) {
            dirs.filter(Files::isDirectory)
                .forEach(p -> System.out.println(p.getFileName().toString()));
        }
    }
}
```

---

## R6: Chat 命令交互流程

**Decision**: `ChatCommand` 核心循环：读 stdin → 空输入跳过 → /quit 退出 → 调 `AgentService.process()` → 打印结果 → 循环。

**Rationale**:
- 流程简单直观，符合用户预期
- 异常时打印 "Error: {message}"，不抛堆栈，继续等待输入
- 不询问用户名，直接用 "cli-user" 默认身份

**Alternatives considered**:
- ❌ 用 JLine 增强交互（自动补全、历史记录）：核心阶段不需要
- ❌ 支持多轮对话上下文切换：复杂度高，核心阶段只做单 Profile 对话

**实现要点**:
```java
@Command(name = "chat")
public class ChatCommand implements Runnable {
    @Override
    public void run() {
        // 1. 加载 Agent
        agentLoader.loadAgents(Path.of(System.getProperty("user.dir")));
        if (profileRegistry.size() == 0) {
            System.err.println("No agents found. Run 'oryxos init' first.");
            return;
        }
        
        // 2. 解析 Profile
        Profile profile = resolveProfile();
        
        // 3. 创建 Session
        Session session = sessionManager.getOrCreateSession("cli", "cli-user", profile.getName());
        
        // 4. 交互循环
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
}
```

---

## R7: 测试策略

**Decision**: 8 个测试类，分三层：
1. **轻命令测试**（不启动 Spring）：ProfileCommandTest、ProviderCommandTest、ToolCommandTest、StatusCommandTest、InitCommandTest
2. **重命令测试**（Mock 或启动 Spring）：ChatCommandTest、StartupVerifierTest
3. **集成测试**（@Tag("integration")）：SessionRepositoryTest

**Rationale**:
- 轻命令测试快速，不依赖 Spring Context
- 重命令测试用 Mockito 隔离外部依赖
- 集成测试验证 SQLite 持久化

**Alternatives considered**:
- ❌ 所有测试都启动 Spring：慢，违背轻重分离原则
- ❌ 不写集成测试：无法验证 SQLite 交互

**测试覆盖点**：
- ProfileCommandTest（4 点）：list/create/show/delete
- ProviderCommandTest（2 点）：provider list
- ToolCommandTest（2 点）：tool list
- StatusCommandTest（1 点）：status 输出
- InitCommandTest（2 点）：幂等性
- ChatCommandTest（4 点）：/quit、空输入、异常处理、正常调用
- SessionManagerTest（2 点）：幂等性、三元组唯一性
- SessionRepositoryTest（1 点，@Tag("integration")）：messages_json 持久化

---

## Summary of Decisions

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 轻重路由 | 白名单硬编码 | 简单直观 |
| 启动验证 | 三步统一验证 | 覆盖完整性 |
| 命令分组 | Picocli subcommands | 原生支持 |
| SessionManager | ConcurrentHashMap | 核心阶段不持久化 |
| 轻命令文件操作 | 直接 Files API | 不依赖 Spring |
| Chat 交互 | 简单 while 循环 | 符合用户预期 |
| 测试策略 | 三层分离 | 快慢隔离 |

**所有 NEEDS CLARIFICATION 已解决，可进入 Phase 1。**
