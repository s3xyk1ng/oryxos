# CLI 模块规格说明书

**Feature Name**: CLI Module - Message Gateway  
**Feature Number**: 004  
**Created**: 2026-10-20  
**Status**: Draft

---

## 1. Overview

### 1.1 模块定位

CLI 是 OryxOS 的**消息进出之门**——用户通过命令行与 Agent 交互的唯一入口。它不处理业务逻辑，只负责读输入、调服务、格式化输出。

### 1.2 背景与价值

Day 1 交付了 Provider（LLM 调用能力），Day 2 交付了 ReAct 引擎（Agent 大脑）。但没有 CLI，用户无法跟 Agent 对话。CLI 模块解决"用户怎么跟 Agent 说话"的问题，让整个系统从"能跑"变成"能用"。

### 1.3 核心职责

1. 命令解析与路由（Picocli）
2. 交互式对话循环（chat）
3. 服务进程管理（serve/gateway）
4. 文件操作（profile CRUD、init）
5. 状态查询与格式化输出

---

## 2. User Scenarios

### 场景 1：交互式对话

用户在终端输入 `oryxos chat weather`，进入交互式多轮对话：
- 系统加载 Agent，选定 Profile
- 创建 Session（默认用户身份 "cli-user"）
- 显示 `>` 提示符，等待用户输入
- 用户输入消息 → Agent 响应 → 继续等待
- 用户输入 `/quit` → 退出对话

### 场景 2：创建 Agent Profile

用户输入 `oryxos profile create test-agent`：
- 系统在 `.oryxos/agents/test-agent/` 下生成 AGENT.md 模板
- 模板包含 frontmatter（name/description/provider/model 等）
- 用户编辑 AGENT.md 填入实际配置

### 场景 3：查看 Agent 列表

用户输入 `oryxos profile list`：
- 系统扫描 `.oryxos/agents/` 目录
- 毫秒级返回所有 Agent 名称列表
- 不启动 Spring，无启动延迟

---

## 3. Functional Requirements

### FR1: 命令分类

11 个命令分 3 类：

**跑 Agent**（重命令，启动 Spring）：
- `chat [AGENT]` - 交互式多轮对话
- `serve` - 启动 Web 服务
- `gateway` - 守护进程，挂载多通道

**查看状态**（轻命令，不启动 Spring）：
- `status` - 系统总览
- `profile list` - 列出所有 Profile
- `profile create <NAME>` - 创建新 Profile
- `profile show <NAME>` - 查看 Profile 详情
- `profile delete <NAME>` - 删除 Profile
- `provider list` - 列出已配置 Provider
- `tool list` - 列出可用 Tool

**初始化**（轻命令）：
- `init` - 初始化工作区

### FR2: 轻重命令分离

- **轻命令**：`init`、`status`、`profile *`、`provider list`、`tool list` 直接执行，不启动 Spring，毫秒级响应
- **重命令**：`chat`、`serve`、`gateway` 启动 Spring，显式声明 `scanBasePackages`，统一做 Bean 断言 + 数据访问层读写验证

### FR3: Chat 核心循环

```
读 stdin → 是 /quit? → 否 → AgentService.process() → 打印 stdout → 回到读 stdin
```

- 空输入跳过，不触发 process()
- 异常时打印 "Error: {message}"，继续循环
- 不询问用户名，直接用默认身份 "cli-user"

### FR4: Profile 文件操作

- `profile create`：在 `.oryxos/agents/<NAME>/AGENT.md` 生成模板
- `profile show`：读取并展示 AGENT.md 内容
- `profile delete`：删除 `.oryxos/agents/<NAME>/` 目录
- `profile list`：扫描目录列出所有 Agent

### FR5: Session 管理

- SessionManager 保持内存版（ConcurrentHashMap）
- `getOrCreateSession(channel, userId, profileName)` 按三元组生成唯一 session_id
- 同一三元组幂等，不同三元组生成不同 Session
- session list 命令本次不做，留到持久化阶段

---

## 4. Out of Scope

以下明确不做：

- **session list 命令**：留到 SessionManager 持久化时再做
- **Session 写入 SQLite**：核心阶段保持内存版
- **gateway 通道挂载实现**：核心阶段只搭骨架
- **用户名交互**：chat 直接用默认身份 "cli-user"
- **Provider/Tool 数据持久化查询**：轻命令只读文件或轻量扫描

---

## 5. Success Criteria

### 5.1 自动化验收

- [ ] 8 个测试类 18 个验收点全绿（`mvn test`）
- [ ] `mvn clean verify` BUILD SUCCESS（含 Spotless/Checkstyle/SpotBugs/PMD）
- [ ] 前序天测试回归绿（Day 1 + Day 2 测试不受影响）

### 5.2 人工验收

- [ ] 轻命令响应 < 100ms（无 Spring 启动日志）
- [ ] `oryxos profile create` 生成正确模板
- [ ] `oryxos chat` 能完成多轮对话
- [ ] `/quit` 正常退出
- [ ] 空输入不报错
- [ ] 异常时友好提示（不抛堆栈）

### 5.3 性能指标

- 轻命令响应时间：< 100ms
- 重命令 Spring 启动时间：< 5s（可接受）

---

## 6. Key Entities

### 6.1 命令（Command）

| 属性 | 说明 |
|------|------|
| name | 命令名（如 chat、profile） |
| type | heavy（重命令）或 light（轻命令） |
| description | 命令描述 |
| subcommands | 子命令列表（如 profile 下有 list/create/show/delete） |

### 6.2 Profile（Agent 配置）

| 属性 | 说明 |
|------|------|
| name | Agent 名称 |
| description | Agent 描述 |
| provider | 绑定的 LLM Provider 名称 |
| model | 模型名称 |
| max_iterations | 最大 ReAct 循环次数 |
| max_history_turns | 最大历史轮数 |

### 6.3 Session（会话）

| 属性 | 说明 |
|------|------|
| sessionId | SHA-256(channel + ":" + userId + ":" + profileName) 前 64 字符 |
| profileName | 关联的 Agent 名称 |
| channel | 渠道（如 "cli"） |
| userId | 用户标识（如 "cli-user"） |
| messages | 对话历史（List<Message>） |
| status | 状态（active/archived） |

---

## 7. Dependencies

### 7.1 前序交付物

- **Day 1 Provider 模块**：ProviderService、ProviderRegistry
- **Day 2 ReAct 模块**：AgentService、ReActLoop、SessionManager、ProfileRegistry、AgentLoader

### 7.2 外部依赖

- Picocli 4.7.6（已集成）
- Spring Boot 3.x（已集成）
- SQLite + Flyway（sessions 表已存在 V1 迁移脚本）

---

## 8. Assumptions

1. 用户已运行 `oryxos init` 初始化工作区
2. 用户已创建至少一个 Agent Profile（AGENT.md）
3. Provider 配置正确（application.yml + 环境变量 API Key）
4. 核心阶段不要求 Session 持久化，重启后会话历史丢失可接受

---

## 9. Acceptance Scenarios

### 场景 A：轻命令不启动 Spring

**Given** 用户输入 `oryxos profile list`  
**When** 命令执行  
**Then** 毫秒级返回，无 Spring 启动日志

### 场景 B：Profile 创建幂等

**Given** 用户已创建 `test-agent`  
**When** 用户再次输入 `oryxos profile create test-agent`  
**Then** 提示 "Profile already exists: test-agent"，不覆盖

### 场景 C：Chat 核心循环

**Given** 用户输入 `oryxos chat weather`  
**When** 用户输入消息 → Agent 响应 → 输入 `/quit`  
**Then** 对话正常进行，/quit 后退出

### 场景 D：空输入跳过

**Given** 用户在 chat 模式下  
**When** 用户直接回车（空输入）  
**Then** 不报错，重新显示 `>` 提示符

### 场景 E：异常友好提示

**Given** Provider 配置错误（如 API Key 无效）  
**When** 用户输入 `oryxos chat weather` 并发送消息  
**Then** 打印 "Error: {message}"，不抛堆栈，继续等待输入

---

## 10. Change Log

| Date | Change | Author |
|------|--------|--------|
| 2026-10-20 | Initial version | AI |
