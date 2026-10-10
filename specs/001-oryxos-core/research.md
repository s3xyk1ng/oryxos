# 技术决策研究

本文档记录 OryxOS 核心阶段的关键技术决策、选型理由和备选方案评估。

---

## RD-001: ReAct Loop 实现方式

**Decision**: 自实现 ReAct loop（`ReActLoop` + `PromptBuilder` + `ToolExecutor`），不依赖 Spring AI 的 Agent 抽象。

**Rationale**:
- Agent 核心循环是 OryxOS 最关键的代码，必须完全可控
- Spring AI 的自动 tool 执行机制不透明，调试困难，且会导致 tool 被调两次
- 自实现核心循环仅数十行 Java 代码，工程量可控
- 保留未来定制循环行为的空间（流式输出、多 Agent 委托等）

**Alternatives considered**:
- Spring AI `ChatClient` 自动执行链：被否决，因为无法精确控制 tool 调度时机
- LangChain4j Agent 抽象：被否决，引入额外依赖且控制力不足

---

## RD-002: Spring AI 使用边界

**Decision**: 只用 Spring AI 的 Provider 抽象 + 协议转换 + `@Tool` Schema 生成，**禁用**自动 tool 执行。

**Rationale**:
- Spring AI Alibaba 已做好 10+ 主流 LLM 的 connector，复用避免重复造轮子
- `@Tool` 注解的 JSON Schema 生成是现成能力，省去手写 Schema
- 必须显式禁用自动执行，否则 tool 被调两次

**Alternatives considered**:
- 完全不用 Spring AI，自己实现 Provider 抽象：被否决，工程量过大且无必要
- 全量使用 Spring AI（含自动执行）：被否决，违反宪法原则 II

---

## RD-003: 执行模型

**Decision**: 同步阻塞 + JDK 21 Virtual Threads，不使用响应式编程。

**Rationale**:
- 同步代码直观、易调试，企业 Java 开发者熟悉
- Virtual Threads 在 IO 阻塞时自动让出 OS 线程，单节点支撑数千并发
- Spring MVC + Virtual Threads 组合成熟，单机撑千级并发
- 扩展阶段 SSE 用 `SseEmitter` 即可，不需要 WebFlux

**Alternatives considered**:
- Spring WebFlux + Reactor：被否决，响应式编程复杂度高，学习曲线陡
- 传统线程池：被否决，线程数受 OS 限制，高并发下资源浪费

---

## RD-004: Tool 注册机制

**Decision**: `@Tool` 注解 + `OryxTool` 抽象层，统一内置 Tool、MCP Tool、@Tool Plugin。

**Rationale**:
- 统一接口让 ReAct 循环不感知 Tool 来源，新增 Tool 类型只需适配器
- `@Tool` 注解是 Spring AI 标准做法，社区熟悉
- `OryxTool` 接口在 Spring AI `@Tool` 之上加一层隔离，避免 ReAct 循环直接依赖 Spring AI

**Alternatives considered**:
- 每种 Tool 类型单独处理：被否决，ReAct 循环需要大量 if-else 分支
- 完全自实现注解系统：被否决，无法复用 Spring AI 的 Schema 生成

---

## RD-005: 持久化选型

**Decision**: SQLite + Spring Data JPA + Flyway，`MEMORY.md` 文件做长期记忆。

**Rationale**:
- SQLite 嵌入式、零配置、单文件，符合"单二进制部署"定位
- Spring Data JPA 提供标准 ORM，切换数据库只需改配置
- Flyway 管理 schema 迁移，`db/migration/{vendor}/` 双轨目录支持 SQLite 和 PostgreSQL
- `MEMORY.md` 零依赖、人可读、git 可跟踪

**Alternatives considered**:
- H2 嵌入式数据库：被否决，SQLite 生态更广、工具链更成熟
- PostgreSQL 核心阶段就用：被否决，需要外部进程，违反单二进制定位
- 纯文件存储（JSON/YAML）：被否决，关系型查询和事务支持不足

---

## RD-006: Provider Name 映射

**Decision**: 显式维护 provider name 到 `ChatModel` 的映射，不靠 Spring 容器类型扫描。

**Rationale**:
- 多 Provider 并存时，多个 `ChatModel` Bean 类型相同，类型扫描无法区分
- 显式映射（`deepseek` → ChatModel-A, `kimi` → ChatModel-B）清晰无歧义
- Profile 通过 provider name 引用，配置直观

**Alternatives considered**:
- 按 Bean name 自动匹配：被否决，Bean name 受 Spring 命名规则影响不可靠
- 按 `@Qualifier` 注解区分：可行但不够灵活，配置文件方式更直观

---

## RD-007: Sandbox 策略

**Decision**: 接口先行——`Sandbox` 抽象接口 + `WhitelistSandbox`（应用层白名单）实现。

**Rationale**:
- 接口独立于任何实现，未来换重隔离方案不改调用方
- 应用层白名单核心阶段够用，防模型犯傻误操作
- `SecurityManager` 在 JDK 17 废弃、JDK 21 不可用，不可选
- 扩展阶段按信号驱动升级：容器隔离 → microVM

**Alternatives considered**:
- Java SecurityManager：被否决，JDK 21 已不可用
- Docker 容器隔离核心阶段就做：被否决，工程量过大
- 不做 Sandbox：被否决，缺乏基本安全保障

---

## RD-008: MCP 传输协议

**Decision**: 核心阶段同时支持 stdio + SSE 两种 MCP 传输协议。

**Rationale**:
- stdio 覆盖大多数社区 MCP server（GitHub MCP、filesystem MCP 等）
- SSE 覆盖远程 MCP server 场景
- 两种协议在 MCP Java SDK 中均有支持，工程增量可控

**Alternatives considered**:
- 仅 stdio：被否决，远程 MCP server 无法接入
- 仅 SSE：被否决，大多数社区 MCP server 使用 stdio
- 两种都支持：✅ 选定，覆盖面最广

---

## RD-009: Memory 后端架构

**Decision**: `LongTermMemoryStore` 可插拔接口，三档后端（Markdown/SQLite/Mem0），配置切换。

**Rationale**:
- 接口墙让存储后端切换不影响上层（PromptBuilder/MemoryTools/ReActLoop）
- MarkdownMemoryStore 零依赖、人可读，是核心阶段默认档
- SqliteMemoryStore 复用已有 SQLite，记忆量大时升级
- Mem0MemoryStore 接自托管 Mem0，语义检索

**Alternatives considered**:
- 直接操作 MEMORY.md 不抽象接口：被否决，无法升级
- 核心阶段就引入向量库：被否决，违反单二进制定位

---

## RD-010: 定时任务实现

**Decision**: 核心阶段做完整实现——`AgentScheduler` + 状态持久化（SQLite）+ 执行历史 + 管理端点。

**Rationale**:
- 任务状态和执行历史重启不丢，运营方可查可控
- `ScheduledTaskStore` 接口在 core，`JpaScheduledTaskStore` 实现在 storage（依赖倒置）
- `reconcile` 机制为同一配置复用稳定 scheduleId，改 key 时旧记录退役不删历史
- v2 管理端点（`/api/v2/schedules`）提供查看/执行历史/立即执行/启用停用

**Alternatives considered**:
- 仅内存状态，重启重新注册：被否决，执行历史丢失
- 静态 `@Scheduled` 注解：被否决，触发规则要按 Profile 动态生成
- 完整实现：✅ 选定，运营可管理性是核心需求

---

## RD-011: CLI 用户身份

**Decision**: CLI 启动时交互式提示用户输入用户名，用于 Session ID 联合标识。

**Rationale**:
- Session ID 由 Channel + 用户 + Agent 联合生成，CLI 场景需要用户标识
- 交互式提示直观，用户无需记忆额外参数
- 与 IM Channel 通过平台账号自动获取用户身份的方式对称

**Alternatives considered**:
- 固定默认值（`cli-user`）：被否决，多用户共用时 Session 冲突
- `--user` 命令行参数：可作为补充，但交互式提示更友好

---

## RD-012: 日志框架

**Decision**: Logback + SLF4J，结构化 JSON 日志。

**Rationale**:
- Spring Boot 默认日志实现，零配置
- SLF4J 是标准日志门面，切换实现不影响业务代码
- 结构化 JSON 便于后续接入 ELK/SIEM 等日志分析系统

**Alternatives considered**:
- Log4j2：被否决，Logback 已是 Spring Boot 默认，无切换必要
