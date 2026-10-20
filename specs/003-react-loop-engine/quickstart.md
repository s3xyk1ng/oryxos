# Quickstart Validation: ReAct 模块

## 前置条件

- Day 1 Provider 模块已交付并测试通过
- DeepSeek API Key 已配置（`DEEPSEEK_API_KEY` 环境变量）
- SQLite 数据库可用

## 验证场景

### 场景 1：单测全绿

```bash
mvn clean verify
```

**期望**: BUILD SUCCESS，0 failures，0 errors，48+ 个测试通过

### 场景 2：端到端冒烟

1. 启动应用：`java -jar oryxos-cli.jar serve`
2. 通过 CLI 发送消息
3. 观察 LLM 响应返回

**期望**: 消息进 → LLM 响应 → 最终回复，全链路通

### 场景 3：审计落库

1. 执行场景 2 后
2. 查 SQLite `llm_calls` 表：应有新记录
3. 查 SQLite `tool_invocations` 表：如果触发了工具调用，应有记录

**期望**: 审计字段完整（session_id, tool_name, success, duration_ms, tool_call_id）

### 场景 4：死循环兜底

1. 配置一个总是返回 tool_call 的 mock 或真实 LLM
2. 观察循环是否在 maxIterations（10）后强制终止

**期望**: 不无限循环，强制返回最后一次响应

### 场景 5：AGENT.md 缺失

1. 临时删除 `.oryxos/agents/{agentName}/AGENT.md`
2. 发消息触发 Agent

**期望**: 抛出 AgentConfigException，不静默降级

## 人工验证项

| # | 验证项 | 通过标准 |
|---|--------|---------|
| 1 | 端到端冒烟 | 全链路通 |
| 2 | 真实 LLM 调用 | DeepSeek 正常返回 |
| 3 | 真实 Tool 执行 | 内置 Tool 执行成功 |
| 4 | 审计落库 | SQLite 有记录 |
| 5 | Prompt 含时间 | LLM 能回答今天几号 |
| 6 | Demo 天气对话 | http_get → 数据 → 建议 |
| 7 | AGENT.md 缺失报错 | 抛异常 |
| 8 | 无禁用项 | grep 确认 |
| 9 | 旧代码清理 | 5 个文件已删 |
| 10 | harness 覆盖 | mvn test 全绿 |
