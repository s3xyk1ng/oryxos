# Implementation Plan: Provider 模块——LLM 协议翻译层

**Branch**: `001-day1-provider-refactor` | **Date**: 2026-10-13 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-provider-module/spec.md`

## Summary

重构 Provider 模块为课件定义的三层架构：oryxos-core 定义接口与数据模型（ProviderService/ProviderRegistry/ProviderDef/ProviderResponse 等），oryxos-provider 实现核心逻辑（SpringAiProviderServiceImpl + 指纹缓存 + ToolSchemaAdapter + AuditLogger + Bootstrap），oryxos-storage 扩展 LlmCallEntity 审计字段。核心约束：只用 Spring AI ChatModel.call()，禁用 ChatClient/Advisor/自动 Tool 执行。

## Technical Context

**Language/Version**: JDK 21

**Primary Dependencies**: Spring Boot 3.4.0, Spring AI 1.0.0-M6 (spring-ai-openai-spring-boot-starter), Spring Data JPA, SQLite, Picocli

**Storage**: SQLite + Spring Data JPA（手工建表脚本，不依赖 ddl-auto=update）

**Testing**: JUnit 5 + Mockito（单测）+ Spring Boot Test（集成冒烟），@Tag("integration") 标记 CI 跳过的冒烟测试

**Target Platform**: 可执行 JAR（java -jar），JDK 21+

**Project Type**: Maven 多模块后端服务 + CLI

**Performance Goals**: 指纹缓存命中时零开销复用 ChatModel 实例

**Constraints**: 构建门禁含 Spotless/Checkstyle/SpotBugs/FindSecBugs/PMD，同步执行模型，无 Reactor/WebFlux

**Scale/Scope**: 核心阶段单 Agent 单 Provider 调用，~20 个新增/重构类，~6 个测试类

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 状态 | 说明 |
|------|------|------|
| I. 自实现 ReAct Loop | ✅ PASS | Provider 只返回原始 tool_calls，不执行 Tool |
| II. Spring AI 边界 | ✅ PASS | 只用 ChatModel.call()，不用 ChatClient/Advisor |
| III. 同步执行 + Virtual Threads | ✅ PASS | chat() 是同步方法，无 Reactor/WebFlux |
| IV. OryxTool 抽象 | ✅ PASS | ToolSchemaAdapter 将 OryxTool 转为 FunctionCallback |
| V. Plugin Tool 三档接入 | ✅ PASS | 不涉及（Day 1 只处理 schema 转换） |
| VI. 单二进制部署 | ✅ PASS | 无新外部依赖 |
| VII. 审计 Day One | ✅ PASS | llm_calls 表成功/失败双记录 |

**模块边界**：
- oryxos-core：接口 + 数据模型 ✅
- oryxos-provider：实现 ✅
- oryxos-storage：审计实体 + Repository ✅
- 未拆分 oryxos-tool ✅

**Post-Phase 1 复核**：所有 7 条原则通过，无违反项。

## Project Structure

### Documentation (this feature)

```text
specs/002-provider-module/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── provider-api.md
├── checklists/
│   └── requirements.md
├── spec.md
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
oryxos-core/src/main/java/com/oryxos/core/
├── agent/
│   ├── ProviderService.java          # interface（新增，对外唯一门面）
│   ├── ProviderRegistry.java         # interface（新增，替代现有 ProfileRegistry 混淆命名）
│   └── ProfileRegistry.java          # 已有，保持不变
├── model/
│   ├── ProviderDef.java              # record（新增）
│   ├── ProviderResponse.java         # record（新增）
│   ├── ToolCall.java                 # record（新增）
│   ├── Usage.java                    # record（新增）
│   ├── Prompt.java                   # record（新增，messages + tools）
│   ├── ProviderNotFoundException.java # exception（新增）
│   └── ProviderCallException.java    # exception（新增）
└── (Profile.java 已有，需新增 model + temperature 字段)

oryxos-provider/src/main/java/com/oryxos/provider/
├── SpringAiProviderServiceImpl.java  # class（新增，核心实现）
├── ToolSchemaAdapter.java            # class（新增，schema 转换 + extractToolCalls）
├── AuditLogger.java                  # class（新增，写 llm_calls）
├── ProviderChatModelFactory.java     # class（新增，指纹缓存）
├── InMemoryProviderRegistry.java     # class（新增，ProviderRegistry 内存实现）
├── ProviderBootstrap.java            # class（新增，ApplicationRunner）
├── ProvidersProperties.java          # class（新增，@ConfigurationProperties）
├── ProviderAutoConfiguration.java    # class（新增，Spring 自动配置）
├── (旧文件: ProviderService.java, ProviderConfig.java, FunctionCallingAdapter.java,
│    LlmPortImpl.java, LlmPortFactoryImpl.java → 删除或重构)

oryxos-storage/src/main/java/com/oryxos/storage/
├── entity/
│   └── LlmCallEntity.java           # 扩展：新增 agent_name, status, tool_call_count, error_message
└── repository/
    └── LlmCallRepository.java       # 已有，保持
```

**Structure Decision**: 保持现有 Maven 多模块结构，按课件定义在三个模块内新增/重构类，不新增模块。
