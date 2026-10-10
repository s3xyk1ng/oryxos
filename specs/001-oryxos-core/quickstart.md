# 快速验证指南

本文档提供 OryxOS 核心阶段的端到端验证场景，用于证明 5 大核心能力正确工作。

---

## 前置条件

- JDK 21+
- Maven 3.8+
- 至少一个 LLM Provider 的 API key（DeepSeek 或 Kimi）
- Node.js（用于运行 MCP server，Demo 二需要）

## 环境准备

```bash
# 1. 编译打包
mvn clean package -DskipTests

# 2. 初始化工作区
cd target
java -jar oryxos-cli-1.0.0.jar init

# 3. 配置 Provider（设置环境变量）
export ORYXOS_PROVIDER_DEEPSEEK_API_KEY=sk-xxx
# 或
export ORYXOS_PROVIDER_KIMI_API_KEY=sk-xxx
```

---

## Demo 一：每日天气（验证 US-1 + US-2 + US-4 + FR-030）

**验证能力**：Provider + ReAct + 内置 HTTP Tool + Sandbox + 定时任务

### 准备

```bash
# 创建 Agent 目录
mkdir -p .oryxos/agents/weather-agent

# 编写 AGENT.md
cat > .oryxos/agents/weather-agent/AGENT.md << 'EOF'
---
name: weather-agent
description: 天气查询助手
identity:
  agent_name: 天气助手
  prompt: 你是一个天气查询助手。查询天气后给出简洁的天气报告和穿搭建议。
provider:
  name: deepseek
  model: deepseek-chat
tools:
  - http_get
  - notify
settings:
  max_iterations: 10
schedules:
  - key: morning-weather
    cron: "0 8 * * *"
    zone: Asia/Shanghai
    message: 查询北京今天的天气并给出穿搭建议
---

你是一个天气查询助手。当用户询问天气时，调用 http_get 工具查询天气数据，
然后给出简洁的天气报告和穿搭建议。
EOF
```

### 验证步骤

```bash
# 步骤 1：CLI 交互对话
java -jar oryxos-cli-1.0.0.jar chat --profile weather-agent
# 输入用户名（交互式提示）
# 发送："查一下北京天气并告诉我穿什么"

# 预期结果：
# ✅ Agent 通过 ReAct 循环自主调用 http_get 获取天气数据
# ✅ 根据天气数据生成穿搭建议
# ✅ 完整对话日志正确累积到 Session

# 步骤 2：检查审计记录
curl http://localhost:8080/api/v1/tools
# ✅ 返回可用工具列表包含 http_get、notify

# 步骤 3：验证 Session 持久化
# 重启 OryxOS 后查询 Session
curl http://localhost:8080/api/v1/sessions/{session_id}
# ✅ Session 数据跨重启恢复
```

---

## Demo 二：每日科技日报（验证 US-3 + US-4 + FR-015/016）

**验证能力**：Memory + Plugin Tool 零代码方式 + Skill 绑定 + MCP

### 准备

```bash
# 创建公共 Skill
mkdir -p .oryxos/skills/digest-format
cat > .oryxos/skills/digest-format/SKILL.md << 'EOF'
---
name: digest-format
description: 科技日报组稿格式规范
---

# 科技日报格式

1. **今日要闻**（3-5 条，每条一句话摘要）
2. **重点关注**（与用户偏好相关的深度分析）
3. **一句话点评**
EOF

# 创建 Agent 目录（含 Skill 绑定）
mkdir -p .oryxos/agents/daily-tech-digest/skills
ln -s ../../../skills/digest-format .oryxos/agents/daily-tech-digest/skills/digest-format

cat > .oryxos/agents/daily-tech-digest/AGENT.md << 'EOF'
---
name: daily-tech-digest
description: 每日科技日报
identity:
  agent_name: 科技日报编辑
  prompt: 你是科技日报编辑，负责汇总当日科技新闻并生成日报。
provider:
  name: deepseek
  model: deepseek-chat
tools:
  - http_get
  - save_memory
  - recall_memory
  - read_file
  - notify
mcp_servers:
  - news-mcp
settings:
  max_iterations: 15
schedules:
  - key: daily-digest
    cron: "0 9 * * *"
    zone: Asia/Shanghai
    message: 生成今日科技日报
---

你是科技日报编辑。生成日报时：
1. 先用 recall_memory 查看用户偏好
2. 用 read_file 读取 skills/digest-format/SKILL.md 了解格式规范
3. 调用新闻工具获取当日科技新闻
4. 按格式规范组稿，体现用户偏好
EOF

# 配置 MCP server
cat > .oryxos/mcp_servers.yaml << 'EOF'
servers:
  - name: news-mcp
    transport: stdio
    command: npx -y @modelcontextprotocol/server-fetch
EOF
```

### 验证步骤

```bash
# 步骤 1：第一次对话——写入记忆
java -jar oryxos-cli-1.0.0.jar chat --profile daily-tech-digest
# 发送："我更关注 AI 和芯片方向的新闻"
# 预期：Agent 调用 save_memory 将偏好写入 MEMORY.md

# 步骤 2：重启，验证记忆保持
# 退出 CLI，重新进入
java -jar oryxos-cli-1.0.0.jar chat --profile daily-tech-digest
# 发送："帮我生成今日科技日报"
# 预期：Agent 调用 recall_memory 读取偏好，日报内容侧重 AI 和芯片

# 步骤 3：检查 MEMORY.md
cat .oryxos/memory/MEMORY.md
# ✅ 包含用户偏好记录

# 步骤 4：验证 Skill 按需加载
# 检查 Agent 的 skills/ 软连接指向正确的公共 Skill 目录
ls -la .oryxos/agents/daily-tech-digest/skills/
# ✅ digest-format -> ../../../skills/digest-format
```

---

## Demo 三：Web Service 集成（验证 US-5）

**验证能力**：REST API 完整链路 + Session 持久化 + 审计

### 准备

```bash
# 启动 HTTP API 服务
java -jar oryxos-cli-1.0.0.jar serve
# 默认端口 8080
```

### 验证步骤

```bash
# 步骤 1：健康检查
curl http://localhost:8080/api/v1/health
# ✅ 返回 {"status": "UP", ...}

# 步骤 2：查看可用 Agent
curl http://localhost:8080/api/v1/profiles
# ✅ 返回已注册的 Agent 列表

# 步骤 3：创建会话
curl -X POST http://localhost:8080/api/v1/sessions \
  -H "Content-Type: application/json" \
  -d '{"profile_name":"weather-agent","channel":"http","user_id":"test-user"}'
# ✅ 返回 session_id

# 步骤 4：发送消息
curl -X POST http://localhost:8080/api/v1/sessions/{session_id}/messages \
  -H "Content-Type: application/json" \
  -d '{"content":"查一下北京天气"}'
# ✅ Agent 处理并返回响应

# 步骤 5：查询历史
curl http://localhost:8080/api/v1/sessions/{session_id}
# ✅ 返回完整对话记录

# 步骤 6：归档会话
curl -X DELETE http://localhost:8080/api/v1/sessions/{session_id}
# ✅ 会话状态变为 archived

# 步骤 7：无状态调用
curl -X POST http://localhost:8080/api/v1/agents/weather-agent/invoke \
  -H "Content-Type: application/json" \
  -d '{"message":"今天天气怎么样","user_id":"test-user"}'
# ✅ 返回 Agent 响应

# 步骤 8：查看可用工具
curl http://localhost:8080/api/v1/tools
# ✅ 返回工具列表（内置 + MCP）

# 步骤 9：查看长期记忆
curl http://localhost:8080/api/v1/memory
# ✅ 返回 MEMORY.md 内容

# 步骤 10：查看运行信息
curl http://localhost:8080/api/v1/info
# ✅ 返回版本、运行时间、Agent 数等
```

---

## 定时任务验证（补充验证 FR-030）

```bash
# 查看定时任务列表
curl http://localhost:8080/api/v2/schedules
# ✅ 返回已注册的定时任务

# 查看执行历史
curl http://localhost:8080/api/v2/schedules/{schedule_id}/executions
# ✅ 返回执行历史记录

# 立即执行一次
curl -X POST http://localhost:8080/api/v2/schedules/{schedule_id}/run
# ✅ 触发执行，返回 session_id

# 停用任务
curl -X PUT http://localhost:8080/api/v2/schedules/{schedule_id} \
  -H "Content-Type: application/json" \
  -d '{"enabled": false}'
# ✅ 任务被停用

# 按 Agent 精确定位执行
curl -X POST http://localhost:8080/api/v2/agents/weather-agent/schedules/morning-weather/run
# ✅ 触发执行
```

---

## CLI 命令验证（补充验证 FR-028/029）

```bash
# 12 个 CLI 命令逐一验证
java -jar oryxos-cli-1.0.0.jar init           # 初始化工作区
java -jar oryxos-cli-1.0.0.jar status          # 查看状态
java -jar oryxos-cli-1.0.0.jar chat            # 交互对话
java -jar oryxos-cli-1.0.0.jar serve           # HTTP API 服务
java -jar oryxos-cli-1.0.0.jar gateway         # 守护进程
java -jar oryxos-cli-1.0.0.jar profile list    # 列出 Profile
java -jar oryxos-cli-1.0.0.jar profile create test  # 创建 Profile
java -jar oryxos-cli-1.0.0.jar profile show test    # 查看 Profile
java -jar oryxos-cli-1.0.0.jar profile delete test  # 删除 Profile
java -jar oryxos-cli-1.0.0.jar provider list   # 列出 Provider
java -jar oryxos-cli-1.0.0.jar tool list       # 列出 Tool
java -jar oryxos-cli-1.0.0.jar session list    # 列出 Session
```

---

## 验收检查清单

| # | 检查项 | Demo | 状态 |
|---|--------|------|------|
| 1 | Agent 通过 ReAct 循环自主完成多步骤任务 | 一 | ☐ |
| 2 | Provider 抽象正确路由到配置的 LLM | 一 | ☐ |
| 3 | 跨会话记忆保持（写入 → 重启 → 引用） | 二 | ☐ |
| 4 | 零代码 Agent 目录 + MCP 完成新场景上线 | 二 | ☐ |
| 5 | Skill 绑定正确，正文按需加载 | 二 | ☐ |
| 6 | REST API 10 个核心端点全部可用 | 三 | ☐ |
| 7 | Session 持久化跨重启恢复 | 三 | ☐ |
| 8 | 审计表 `tool_invocations` + `llm_calls` 有写入 | 全部 | ☐ |
| 9 | 定时任务到点自动触发 + 管理端点可用 | 补充 | ☐ |
| 10 | 12 个 CLI 命令全部可用 | 补充 | ☐ |
| 11 | Sandbox 白名单校验生效 | 一 | ☐ |
| 12 | "钟推"和"人推"复用同一条 AgentService 链路 | 一 | ☐ |
