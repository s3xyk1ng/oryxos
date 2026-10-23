# Tool 模块重构：Sandbox 下沉 + 条件注册 + @Tool 注解 + 失败隔离

## Overview

Tool 模块是 OryxOS Agent 的能力扩展层。本次重构解决四个问题：Sandbox 校验从 ToolExecutor 下沉到工具自身实现自治；新增条件注册机制让不满足条件的工具不暴露给 LLM；提供 @Tool 注解方式让 Java 开发者快速扩展工具；工具执行失败不拖垮整个 ReAct 循环。

## User Scenarios & Testing

### 场景一：Java 开发者用注解扩展工具

Java 开发者在 Spring Bean 方法上加 @Tool 注解，声明工具名和描述。框架启动时自动扫描该方法，包装为 OryxTool 注册到 ToolRegistry。Agent 对话时 LLM 可以看到并调用该工具。

**验收**：启动后 `oryxos tool list` 能看到 @Tool 注册的工具；调用时方法正确执行并返回结果。

### 场景二：通知渠道为空时工具自动隐藏

数据库中没有注册任何通知渠道。框架启动时 NotifyTool 的 isAvailable() 返回 false，工具不注册到 ToolRegistry。LLM 看不到 notify 工具，不会尝试调用。

**验收**：渠道表空时启动，tool list 中无 notify 工具；注册渠道后重启，notify 工具出现。

### 场景三：内置工具各自校验 Sandbox

Agent 调用 read_file 读取文件。ReadFileTool 在 execute() 第一行调用 sandbox.enforce(FILE_READ, path)，校验通过才执行文件读取。如果路径不在白名单，抛 SecurityException，ToolExecutor 兜底为 ToolResult.fail()。

**验收**：白名单内路径正常读取；白名单外路径返回失败，不读文件。

### 场景四：工具失败不影响其他工具

同一轮 ReAct 迭代中 LLM 返回两个 tool_call。第一个工具执行失败（抛异常），第二个工具正常执行。失败结果和成功结果都写入对话历史，ReAct 循环继续。

**验收**：第一个工具失败不中断第二个工具的执行；循环不中断；对话历史包含两个工具的结果。

## Functional Requirements

### Sandbox 下沉

- FR1: 每个 Java 工具（FileTools / ShellTools / HttpTools）在 execute() 第一行调用 sandbox.enforce()，传入对应的 ActionType 和 target
- FR2: ToolExecutor 删除 performSandboxCheck() 方法，不再做 Sandbox 分发
- FR3: Sandbox 为可选依赖（@Autowired(required = false)），未配置时工具正常工作

### 条件注册

- FR4: 新增 ConditionalTool 接口，包含 boolean isAvailable() 方法
- FR5: ToolAutoRegistrar 注册时检查 ConditionalTool，isAvailable() 返回 false 的工具不注册
- FR6: 不实现 ConditionalTool 的普通工具照常注册

### NotifyTool 独立

- FR7: NotifyTool 从 NotifyTools 内部类提为独立 @Component，实现 OryxTool + ConditionalTool
- FR8: NotifyTool.isAvailable() 根据通知渠道表是否为空返回 true/false
- FR9: NotifyTools 包装类删除

### @Tool 注解体系

- FR10: 新增 @Tool 注解（@Target(METHOD)），包含 name 和 description 属性
- FR11: ToolMethodScanner 启动时扫描所有 Spring Bean 的 @Tool 方法
- FR12: 每个 @Tool 方法包装为 AnnotatedToolAdapter 注册到 ToolRegistry
- FR13: AnnotatedToolAdapter 实现 OryxTool，通过反射调用原方法

### 失败隔离

- FR14: ToolExecutor 兜底所有异常（包括 RuntimeException），返回 ToolResult.fail()，不向上抛
- FR15: ReActLoop 的 for 循环内加 try-catch 防御，一个工具失败不影响同批次其他工具
- FR16: 失败信息拼接 [retryable] / [not retryable] 信号，基于 ToolResult.retryable 字段

## Success Criteria

- 所有内置工具在 execute() 第一行执行 Sandbox 校验，Sandbox 校验在 IO 操作之前
- 渠道表为空时 notify 工具不出现在 ToolRegistry 中
- @Tool 注解的方法在启动后自动注册为可调用工具
- 单个工具执行失败不导致同批次其他工具执行失败
- 单个工具执行失败不导致 ReAct 循环中断
- mvn clean verify 全绿，0 failures，0 errors
- 前序天（Day 1-4）所有测试回归通过

## Scope

### In Scope

- Sandbox 下沉到 FileTools / ShellTools / HttpTools
- ToolExecutor 重构（删除 Sandbox 分发 + retryable 信号）
- ReActLoop 失败隔离（try-catch 防御）
- ConditionalTool 接口 + ToolAutoRegistrar 条件注册
- NotifyTool 独立 + NotifyTools 删除
- @Tool 注解 + ToolMethodScanner + AnnotatedToolAdapter

### Out of Scope

- MCP 工具的 Sandbox 校验（核心阶段不做）
- 连续失败检测（方案 B，后续完善）
- OryxTool / ToolResult / ToolRegistry 接口修改
- Provider 层逻辑修改
- Sandbox 接口和 WhitelistSandbox 实现修改

## Dependencies and Assumptions

### 依赖

- Day 1：OryxTool / ToolResult / ToolRegistry 接口已存在
- Day 2：ToolExecutor / Sandbox / SandboxAction / AuditPort 已存在
- Day 4：NotifyChannelAdapter / NotifyChannelRepository / WebhookNotifyAdapter / DingTalkNotifyAdapter 已存在
- 骨架代码：FileTools / ShellTools / HttpTools / McpLoader / McpToolAdapter / WhitelistSandbox / ToolAutoRegistrar / NotifyTools

### 假设

- Jackson ObjectMapper 由 Spring Boot 提供
- JDK HttpClient 可用（JDK 21）
- @Tool 注解使用 OryxOS 自定义注解（非 Spring AI 的 @Tool）

## Assumptions

- 核心阶段 @Tool 注解工具的 Sandbox 由工具自身负责，框架不自动加校验
- ToolAutoRegistrar 的条件注册日志包含注册数量和跳过数量
- 失败信息中的 [retryable] / [not retryable] 是给模型的提示信号，不影响执行逻辑
