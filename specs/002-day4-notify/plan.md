# Implementation Plan: Notify 通知推送模块

**Branch**: `004-day4-notify` | **Date**: 2026-10-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-day4-notify/spec.md`

## Summary

重构 Notify 模块：将 Day 1 骨架代码按新设计重构为接口中立的通知推送系统。核心变更包括：
1. 新建 `NotifyTarget` record（type + config Map）
2. 重构 `NotifyChannelAdapter` 接口（去掉 getType()，只保留 send(NotifyTarget, String)）
3. 重构 `WebhookNotifyAdapter`（NotifyTarget 入参 + Jackson ObjectMapper + 5xx 向上抛 + Sandbox TODO 占位）
4. 重构 `NotifyTools`（注入 Repository 查渠道 + 遍历适配器 + 默认渠道 fallback）
5. Profile 新增 `defaultNotifyChannel` 字段 + AgentLoader 解析 frontmatter
6. 14 个测试用例覆盖全部关键回归点

## Technical Context

**Language/Version**: JDK 21

**Primary Dependencies**: Spring Boot 3.x, Spring AI Alibaba, Jackson 2.18.1（已确认在 BOM 中）, JDK HttpClient（内置）

**Storage**: SQLite + Spring Data JPA（notify_channels 表已由 V2 迁移脚本创建）

**Testing**: JUnit 5 + Mockito + JDK 内置 HttpServer（com.sun.net.httpserver，mock webhook 服务端）

**Target Platform**: 本地 JVM（Windows/Linux/macOS）

**Project Type**: 多模块 Maven 项目（oryxos-tool 模块）

**Performance Goals**: webhook 发送超时 10s，不阻塞主线程

**Constraints**: 避开 P3C/ASM 解析不了的 Java 18+ 语法形态；凭证不落明文；不静默失败

**Scale/Scope**: 3 个源文件重构 + 1 个新 record + 2 个文件修改（Profile/AgentLoader）+ 3 个测试类

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 状态 | 说明 |
|------|------|------|
| H1: 代码全英文，注释中文 | ✅ | 所有新增/重构代码遵守 |
| H2: 传统 switch-case | ✅ | 不使用增强 switch 的 default -> 写法 |
| H3: 凭证走环境变量 | ✅ | ${ENV_VAR} 占位符（TODO 占位） |
| H4: 不用 ChatClient/Advisor | ✅ | 只通过 ToolExecutor 调用 |
| H5: 测试方法名英文 + @DisplayName 中文 | ✅ | 遵守 |
| H6: mvn clean verify 全绿 | ✅ | 目标 |
| 不能静默失败 | ✅ | 每层错误向上传递 |
| 校验在前，发送在后 | ✅ | InOrder 测试钉死 |

## Project Structure

### Documentation (this feature)

```text
specs/002-day4-notify/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── checklists/
│   └── requirements.md  # Quality checklist
└── tasks.md             # Phase 2 output (by /speckit-tasks)
```

### Source Code (repository root)

```text
oryxos-tool/src/main/java/com/oryxos/tool/notify/
├── NotifyTarget.java              # 新建：record(type, config)
├── NotifyChannelAdapter.java      # 重构：去掉 getType()，send 入参改为 NotifyTarget
├── WebhookNotifyAdapter.java      # 重构：NotifyTarget 入参 + Jackson + 5xx 抛异常 + Sandbox TODO
└── NotifyTools.java               # 重构：注入 Repository + 遍历适配器 + 默认渠道

oryxos-core/src/main/java/com/oryxos/core/
├── model/Profile.java             # 修改：新增 defaultNotifyChannel 字段
└── agent/AgentLoader.java         # 修改：deriveProfile 新增解析 default_notify_channel

oryxos-tool/src/test/java/com/oryxos/tool/notify/
├── WebhookNotifyAdapterTest.java  # 新建：5 个测试用例
└── NotifyToolsTest.java           # 新建：5 个测试用例

oryxos-core/src/test/java/com/oryxos/core/agent/
└── AgentLoaderNotifyTest.java     # 新建：2 个测试用例（追加到现有测试）
```

**Structure Decision**: 所有 Notify 业务代码落在 oryxos-tool 模块的 notify 包内（与课件和 TechnicalSolution §10 一致）。Profile/AgentLoader 修改在 oryxos-core 模块。测试与实现同包。

## Complexity Tracking

无 Constitution 违规项，此表留空。
