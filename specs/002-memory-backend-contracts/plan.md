# Implementation Plan: Memory Backend Contracts

**Branch**: `006-day6-memory-refactor` | **Date**: 2026-10-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-memory-backend-contracts/spec.md`

## Summary

Refactor the Memory module so that `MemoryService` becomes a thin facade with exactly three upper-layer capabilities (prompt-context assembly, save memory, recall archival memory by keyword), and `LongTermMemoryStore` becomes a three-method pluggable backend contract (`append`, `load`, `recallByKeyword`). Deliver three backend implementations — Markdown (default), SQLite, and Mem0 — that all pass the same contract tests and obey the four backend invariants: no caching, CORE never truncated, Agent explicitly selects scope, and initial recall is simple keyword containment.

## Technical Context

**Language/Version**: JDK 21

**Primary Dependencies**: Spring Boot 3.x, Spring AI Alibaba (BOM-locked), Spring Data JPA, Flyway

**Storage**: SQLite for structured backend; `.oryxos/memory/MEMORY.md` for default file backend; self-hosted Mem0 via REST for external backend

**Testing**: JUnit 5, AssertJ, Mockito, Spring Boot Test, ApplicationContextRunner

**Target Platform**: Single executable JAR (single-binary deployment)

**Project Type**: Multi-module Maven library / CLI / web-service

**Performance Goals**: Prompt assembly re-reads memory every turn; acceptable because CORE is kept small and ARCHIVAL is loaded through a bounded window

**Constraints**: No Reactor/WebFlux; no Spring AI automatic tool execution; no external vector database process; credentials via environment variables only

**Scale/Scope**: Up to thousands of memory entries in SQLite backend; file backend for small local deployments; Mem0 backend delegated to external service

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Self-implemented ReAct Loop | Pass | Memory only provides context and tools, does not take over the loop |
| II. Spring AI boundary — no automatic tool execution | Pass | No ChatClient auto-execution or Advisor introduced |
| III. Synchronous execution + Virtual Threads | Pass | File, JPA, and Mem0 REST calls are synchronous; no Reactor/WebFlux |
| IV. OryxTool abstraction | Pass | `save_memory` / `recall_memory` continue to execute through the unified tool system |
| V. Plugin Tool three-tier access | Pass | MemoryTools may use `@Tool` annotation (tier 3) or internal class; both register through OryxTool |
| VI. Single-binary deployment | Pass | Markdown/SQLite need no extra process; Mem0 is optional self-hosted |
| VII. Audit Day One | Pass | Tool and LLM calls continue to be written through existing AuditPort |

## Project Structure

### Documentation (this feature)

```text
specs/002-memory-backend-contracts/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (created by /speckit-tasks)
```

### Source Code (repository root)

```text
oryxos-core/
└── src/main/java/com/oryxos/core/react/
    └── MemoryPort.java          # abstraction used by PromptBuilder

oryxos-memory/
├── src/main/java/com/oryxos/memory/
│   ├── MemoryScope.java         # CORE / ARCHIVAL enum
│   ├── MemoryService.java       # unified facade
│   ├── LongTermMemoryStore.java # three-method backend contract
│   ├── MarkdownMemoryStore.java # default file backend
│   ├── SqliteMemoryStore.java   # SQLite backend
│   ├── Mem0MemoryStore.java     # self-hosted Mem0 REST backend
│   ├── MemoryTools.java         # save_memory / recall_memory tools
│   └── MemoryPortImpl.java      # MemoryPort implementation delegating to MemoryService
└── src/test/java/com/oryxos/memory/
    ├── LongTermMemoryStoreContractTest.java
    ├── MarkdownMemoryStoreContractTest.java
    ├── SqliteMemoryStoreContractTest.java
    ├── Mem0MemoryStoreContractTest.java
    ├── MemoryServiceTest.java
    ├── MemoryToolsTest.java
    ├── PromptBuilderMemoryIntegrationTest.java
    ├── MemoryBackendSelectionTest.java
    └── support/                 # test helpers, fakes

oryxos-storage/
├── src/main/java/com/oryxos/storage/entity/
│   └── MemoryEntryEntity.java   # JPA mapping for memory_entries
├── src/main/java/com/oryxos/storage/repository/
│   └── MemoryEntryRepository.java
└── src/main/resources/db/migration/sqlite/
    └── V4__create_memory_entries.sql
```

**Structure Decision**: Memory abstractions and all backend implementations stay in `oryxos-memory` to keep the facade self-contained. JPA entity and repository live in `oryxos-storage` so the storage module owns schema and persistence primitives. The core module only keeps the `MemoryPort` abstraction so `PromptBuilder` can remain decoupled from `oryxos-memory`.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations.
