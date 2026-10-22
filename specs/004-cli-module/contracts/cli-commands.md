# CLI Commands Contract

**Feature**: 004-cli-module  
**Date**: 2026-10-20  
**Status**: Complete

---

## Overview

CLI 模块提供 11 个命令，分 3 类：跑 Agent（重命令）、查看状态（轻命令）、初始化（轻命令）。所有命令通过 Picocli 解析参数，轻命令不启动 Spring，重命令启动 Spring 并做启动验证。

---

## Command Categories

### Heavy Commands（重命令）

启动 Spring，显式声明 `scanBasePackages`，统一做 Bean 断言 + 数据访问层读写验证。

| Command | Description | Usage |
|---------|-------------|-------|
| `chat` | Start interactive chat with an Agent | `oryxos chat [AGENT]` |
| `serve` | Start Web Service | `oryxos serve [--port PORT]` |
| `gateway` | Start daemon mounting multiple channels | `oryxos gateway` |

### Light Commands（轻命令）

不启动 Spring，直接文件 I/O，毫秒级响应。

| Command | Description | Usage |
|---------|-------------|-------|
| `init` | Initialize workspace | `oryxos init` |
| `status` | Show system overview | `oryxos status` |
| `profile list` | List all Profiles | `oryxos profile list` |
| `profile create` | Create new Profile | `oryxos profile create <NAME>` |
| `profile show` | Show Profile details | `oryxos profile show <NAME>` |
| `profile delete` | Delete Profile | `oryxos profile delete <NAME>` |
| `provider list` | List configured Providers | `oryxos provider list` |
| `tool list` | List available Tools | `oryxos tool list` |

---

## Command Specifications

### 1. `oryxos chat [AGENT]`

**Type**: Heavy  
**Description**: Start interactive chat with an Agent

**Arguments**:
- `AGENT` (optional): Profile name. If omitted, prompt user to select.

**Behavior**:
1. Load all Agents from `.oryxos/agents/`
2. If no Agents found, print "No agents found. Run 'oryxos init' first." and exit
3. If AGENT not specified, list available Agents and prompt user to select
4. Create or retrieve Session with (channel="cli", userId="cli-user", profileName=AGENT)
5. Enter interactive loop:
   - Print "> " prompt
   - Read user input
   - If input is empty, skip and continue
   - If input is "/quit", exit loop
   - Otherwise, call `AgentService.process(session, input)` and print result
   - If exception occurs, print "Error: {message}" and continue

**Exit Conditions**:
- User inputs "/quit"
- EOF (Ctrl+D)

**Example**:
```bash
$ oryxos chat weather
> What's the weather in Beijing?
The weather in Beijing is sunny, 25°C.
> /quit
```

---

### 2. `oryxos serve [--port PORT]`

**Type**: Heavy  
**Description**: Start Web Service

**Options**:
- `--port PORT` (optional): Port number, default 8080

**Behavior**:
1. Start Spring Boot application with Web Service configuration
2. Run startup verification
3. Print "OryxOS Web Service started on port {PORT}"

**Example**:
```bash
$ oryxos serve --port 8080
OryxOS Web Service started on port 8080
```

---

### 3. `oryxos gateway`

**Type**: Heavy  
**Description**: Start daemon mounting multiple channels

**Behavior**:
1. Start Spring Boot application with gateway configuration
2. Run startup verification
3. Print "OryxOS gateway started"

**Note**: Core stage only implements skeleton. Channel mounting is deferred.

**Example**:
```bash
$ oryxos gateway
OryxOS gateway started
```

---

### 4. `oryxos init`

**Type**: Light  
**Description**: Initialize workspace

**Behavior**:
1. Create `.oryxos/` directory structure:
   - `.oryxos/agents/`
   - `.oryxos/tools/`
   - `.oryxos/config/`
2. Create default `application.yml` in `.oryxos/config/`
3. If `.oryxos/` already exists, print "Workspace already initialized." and exit
4. Print "Workspace initialized at .oryxos/"

**Idempotency**: Running `init` multiple times is safe. Existing files are not overwritten.

**Example**:
```bash
$ oryxos init
Workspace initialized at .oryxos/
```

---

### 5. `oryxos status`

**Type**: Light  
**Description**: Show system overview

**Behavior**:
1. Read `.oryxos/config/application.yml`
2. Count Agents in `.oryxos/agents/`
3. Count configured Providers
4. Print formatted status:
   ```
   OryxOS Status
   =============
   Workspace: .oryxos/
   Agents: 3
   Providers: 2 (deepseek, qwen)
   Tools: 5
   ```

**Example**:
```bash
$ oryxos status
OryxOS Status
=============
Workspace: .oryxos/
Agents: 3
Providers: 2 (deepseek, qwen)
Tools: 5
```

---

### 6. `oryxos profile list`

**Type**: Light  
**Description**: List all Profiles

**Behavior**:
1. Scan `.oryxos/agents/` directory
2. For each subdirectory, read AGENT.md frontmatter
3. Print formatted list:
   ```
   Available Profiles:
   - weather (Weather Agent)
   - translator (Translation Agent)
   - coder (Coding Assistant)
   ```
4. If no Agents found, print "No agents found. Run 'oryxos init' first."

**Example**:
```bash
$ oryxos profile list
Available Profiles:
- weather (Weather Agent)
- translator (Translation Agent)
```

---

### 7. `oryxos profile create <NAME>`

**Type**: Light  
**Description**: Create new Profile

**Arguments**:
- `NAME` (required): Profile name (alphanumeric, underscore, hyphen only)

**Behavior**:
1. Validate NAME format (regex: `^[a-zA-Z0-9_-]+$`)
2. Check if `.oryxos/agents/<NAME>/` already exists
3. If exists, print "Profile already exists: <NAME>" and exit
4. Create `.oryxos/agents/<NAME>/` directory
5. Generate AGENT.md template:
   ```markdown
   ---
   name: <NAME>
   description: TODO: Add description
   provider: deepseek
   model: deepseek-chat
   max_iterations: 10
   max_history_turns: 5
   ---
   
   # <NAME>
   
   TODO: Add system prompt
   ```
6. Print "Profile created: .oryxos/agents/<NAME>/AGENT.md"
7. Print "Edit the file to configure your Agent."

**Example**:
```bash
$ oryxos profile create weather
Profile created: .oryxos/agents/weather/AGENT.md
Edit the file to configure your Agent.
```

---

### 8. `oryxos profile show <NAME>`

**Type**: Light  
**Description**: Show Profile details

**Arguments**:
- `NAME` (required): Profile name

**Behavior**:
1. Check if `.oryxos/agents/<NAME>/AGENT.md` exists
2. If not exists, print "Profile not found: <NAME>" and exit
3. Read and parse AGENT.md frontmatter
4. Print formatted details:
   ```
   Profile: weather
   Description: Weather Agent
   Provider: deepseek
   Model: deepseek-chat
   Max Iterations: 10
   Max History Turns: 5
   
   System Prompt:
   ---
   You are a weather assistant...
   ---
   ```

**Example**:
```bash
$ oryxos profile show weather
Profile: weather
Description: Weather Agent
Provider: deepseek
Model: deepseek-chat
...
```

---

### 9. `oryxos profile delete <NAME>`

**Type**: Light  
**Description**: Delete Profile

**Arguments**:
- `NAME` (required): Profile name

**Behavior**:
1. Check if `.oryxos/agents/<NAME>/` exists
2. If not exists, print "Profile not found: <NAME>" and exit
3. Prompt confirmation: "Delete profile '<NAME>'? [y/N]"
4. If user confirms (y/Y), delete `.oryxos/agents/<NAME>/` recursively
5. Print "Profile deleted: <NAME>"

**Example**:
```bash
$ oryxos profile delete weather
Delete profile 'weather'? [y/N] y
Profile deleted: weather
```

---

### 10. `oryxos provider list`

**Type**: Light  
**Description**: List configured Providers

**Behavior**:
1. Read `.oryxos/config/application.yml`
2. Extract provider configurations
3. Print formatted list:
   ```
   Configured Providers:
   - deepseek (deepseek-chat)
   - qwen (qwen-turbo)
   ```
4. If no providers configured, print "No providers configured. Edit .oryxos/config/application.yml"

**Example**:
```bash
$ oryxos provider list
Configured Providers:
- deepseek (deepseek-chat)
- qwen (qwen-turbo)
```

---

### 11. `oryxos tool list`

**Type**: Light  
**Description**: List available Tools

**Behavior**:
1. Scan `.oryxos/tools/` directory for tool definitions
2. For each tool, read metadata
3. Print formatted list:
   ```
   Available Tools:
   - get_weather (Built-in): Get current weather
   - search_web (MCP): Search the web
   ```
4. If no tools found, print "No tools found."

**Example**:
```bash
$ oryxos tool list
Available Tools:
- get_weather (Built-in): Get current weather
```

---

## Error Handling

### Common Error Codes

| Exit Code | Meaning |
|-----------|---------|
| 0 | Success |
| 1 | General error |
| 2 | Invalid arguments |
| 3 | Workspace not initialized |
| 4 | Profile not found |
| 5 | Provider not configured |

### Error Messages

All error messages follow the format:
```
Error: <message>
```

No stack traces are printed in production mode.

---

## Performance Requirements

| Metric | Target |
|--------|--------|
| Light command response time | < 100ms |
| Heavy command Spring startup time | < 5s |
| Profile file operation | < 50ms |

---

## Testing Requirements

| Test Class | Coverage |
|------------|----------|
| ProfileCommandTest | list/create/show/delete (4 tests) |
| ProviderCommandTest | provider list (2 tests) |
| ToolCommandTest | tool list (2 tests) |
| StatusCommandTest | status output (1 test) |
| InitCommandTest | idempotency (2 tests) |
| ChatCommandTest | /quit, empty input, exception, normal (4 tests) |
| SessionManagerTest | idempotency, uniqueness (2 tests) |
| SessionRepositoryTest | messages_json persistence (1 test, @Tag("integration")) |

**Total**: 8 test classes, 18 acceptance points

---

## Change Log

| Date | Change | Author |
|------|--------|--------|
| 2026-10-20 | Initial version | AI |
