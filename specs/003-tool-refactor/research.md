# Research: Tool 模块重构

**Date**: 2026-10-23

## 无 NEEDS CLARIFICATION 项

本重构所有技术决策已在设计讨论中对齐，无未解决的澄清项。

## 已确认的技术决策

### 1. @Tool 注解：OryxOS 自定义 vs Spring AI @Tool

- **Decision**: 使用 OryxOS 自定义 `@Tool` 注解（`com.oryxos.tool.annotation.Tool`）
- **Rationale**: Spring AI 的 `@Tool` 注解与 Spring AI 框架绑定，OryxOS 禁用 Spring AI 自动工具执行。自定义注解保持独立性
- **Alternatives considered**: 复用 Spring AI @Tool → 拒绝，违反"禁用 Spring AI 自动工具执行"原则

### 2. ConditionalTool 接口位置

- **Decision**: 放 oryxos-core（`com.oryxos.core.model.ConditionalTool`）
- **Rationale**: 被 ToolAutoRegistrar（oryxos-tool）和 NotifyTool（oryxos-tool）共同依赖，接口定义应在 core 层
- **Alternatives considered**: 放 oryxos-tool → 拒绝，core 层接口不应依赖 tool 模块

### 3. Sandbox 注入方式

- **Decision**: `@Autowired(required = false)` 可选注入
- **Rationale**: Sandbox 可能未配置（测试场景或极简部署），工具不应因 Sandbox 缺失而无法使用
- **Alternatives considered**: 必选注入 → 拒绝，增加部署耦合

### 4. ToolExecutor 异常兜底范围

- **Decision**: catch Exception（不 catch Throwable/Error）
- **Rationale**: Error（如 OutOfMemoryError）属于 JVM 级故障，兜住也无意义。Exception 覆盖所有业务异常和运行时异常
- **Alternatives considered**: catch Throwable → 拒绝，连 Error 也兜住会掩盖 JVM 严重问题
