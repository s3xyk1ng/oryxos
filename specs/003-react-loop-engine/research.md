# Research: ReAct 模块

## 无待解决未知项

所有技术决策已在前期的模块设计讨论中完成（见 `docs/dev/day2.md`）。

## 已确认的技术决策

| # | 决策 | 选择 | 理由 |
|---|------|------|------|
| 1 | ReActLoop 循环控制 | 持有 for 循环 | 循环控制 + 消息累积 + 终止判断是原子逻辑 |
| 2 | ReActLoop 是否接收 tools | 否 | Tool 查找由 ToolExecutor 内部处理 |
| 3 | LLM 调用类型 | ProviderService（OryxOS 原生） | 彻底解耦 Spring AI 类型 |
| 4 | LLM 审计 | ProviderService 内置 | 避免重复审计 |
| 5 | Tool 审计 | ToolExecutor finally 块 | 四种情况全覆盖 |
| 6 | ProfileContext | ThreadLocal | 虚拟线程天然隔离 |
| 7 | 历史截断 | maxHistoryTurns 默认 20 轮 | 技术方案 §4.3 明确要求 |
| 8 | Prompt 时间戳 | RFC_1123 格式 | LLM 不知道当前日期 |

## 依赖核实

- Day 1 ProviderService 接口 ✅ 存在
- ProviderResponse / ToolCall / Prompt record ✅ 存在
- ToolInvocationEntity + Repository ✅ 存在
- V1 迁移脚本（tool_invocations 表）✅ 存在
- Spring AI M6 ChatModel.call() ✅ 已锁定
