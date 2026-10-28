# Tasks: Memory Backend Contracts

**Input**: Design documents from `/specs/002-memory-backend-contracts/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Tests are included because the feature specification and harness require them.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure for the Memory module refactor

- [x] T001 Add `oryxos-memory` dependencies on `oryxos-storage` and Spring Boot configuration processor in `oryxos-memory/pom.xml`
- [x] T002 Add `MemoryProperties` configuration class in `oryxos-memory/src/main/java/com/oryxos/memory/MemoryProperties.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core abstractions and contract tests that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T003 [P] Extract `MemoryScope` enum (CORE, ARCHIVAL) into `oryxos-memory/src/main/java/com/oryxos/memory/MemoryScope.java`
- [x] T004 [P] Create `MemoryContent` record (coreSection, archivalSection) in `oryxos-memory/src/main/java/com/oryxos/memory/MemoryContent.java`
- [x] T005 Reduce `LongTermMemoryStore` interface to three methods in `oryxos-memory/src/main/java/com/oryxos/memory/LongTermMemoryStore.java`
- [x] T006 Create `MemoryStoreException` in `oryxos-memory/src/main/java/com/oryxos/memory/MemoryStoreException.java`
- [x] T007 Create abstract `LongTermMemoryStoreContractTest` in `oryxos-memory/src/test/java/com/oryxos/memory/LongTermMemoryStoreContractTest.java`
- [x] T008 Create `MemoryEntryEntity` in `oryxos-storage/src/main/java/com/oryxos/storage/entity/MemoryEntryEntity.java` (`id`, `scope` CHECK CORE/ARCHIVAL, `content` CHECK non-blank, `created_at`)
- [x] T009 Create `MemoryEntryRepository` in `oryxos-storage/src/main/java/com/oryxos/storage/repository/MemoryEntryRepository.java`
- [x] T010 Add Flyway migration `oryxos-storage/src/main/resources/db/migration/sqlite/V4__create_memory_entries.sql`
- [x] T011 Adjust `MemoryPort` interface in `oryxos-core/src/main/java/com/oryxos/core/react/MemoryPort.java` to `String buildMemoryContext(Session session)` returning only CORE memory (no conversation history)

**Checkpoint**: Foundation ready - MemoryScope, MemoryContent, three-method LongTermMemoryStore, contract test shell, SQLite schema, and MemoryPort are in place

---

## Phase 3: User Story 1 - CORE memory is always visible in prompt (Priority: P1) 🎯 MVP

**Goal**: Agent can save a memory as CORE and have it automatically included in subsequent prompt contexts, completely and untruncated.

**Independent Test**: Run `LongTermMemoryStoreContractTest` no-cache and CORE-never-truncated cases against Markdown backend; run `MemoryServiceTest#buildPromptContext_includesCoreAndSessionHistory`.

### Tests for User Story 1

- [x] T012 [P] [US1] Add `load_coreIsNeverTruncated` to `LongTermMemoryStoreContractTest.java`
- [x] T013 [P] [US1] Add `load_afterExternalAppendReturnsLatestState` to `LongTermMemoryStoreContractTest.java`
- [x] T014 [US1] Add `MemoryServiceTest.java` with `buildPromptContext_includesCoreAndSessionHistory` and `buildPromptContext_reloadsMemoryEveryTime`

### Implementation for User Story 1

- [x] T015 [P] [US1] Implement `MarkdownMemoryStore.append` and `.load` with CORE preservation in `oryxos-memory/src/main/java/com/oryxos/memory/MarkdownMemoryStore.java`
- [x] T016 [P] [US1] Implement `MemoryService.buildPromptContext(Session)` returning CORE + session history only in `oryxos-memory/src/main/java/com/oryxos/memory/MemoryService.java`; `MemoryPortImpl` implements `buildMemoryContext(Session)` returning only CORE
- [x] T017 [US1] Update `MemoryPortImpl` to delegate `buildMemoryContext(Session)` in `oryxos-memory/src/main/java/com/oryxos/memory/MemoryPortImpl.java`
- [x] T047 [US1] Adjust `PromptBuilder` in `oryxos-core/src/main/java/com/oryxos/core/react/PromptBuilder.java` to inject CORE memory via `memoryPort.buildMemoryContext(session)` and keep conversation history in `buildChatPrompt` only
- [x] T018 [US1] Create `MarkdownMemoryStoreContractTest` extending contract test in `oryxos-memory/src/test/java/com/oryxos/memory/MarkdownMemoryStoreContractTest.java`
- [x] T019 [US1] Run `mvn test -pl oryxos-memory` and fix failures for US1

**Checkpoint**: User Story 1 is fully functional and testable independently

---

## Phase 4: User Story 2 - ARCHIVAL memory is recalled on demand (Priority: P2)

**Goal**: Agent can save a memory as ARCHIVAL, it does not appear in automatic prompt context, and it can be recalled later by keyword through the `recall_memory` tool.

**Independent Test**: Run `LongTermMemoryStoreContractTest` ARCHIVAL-only recall cases; run `MemoryToolsTest#saveMemory_acceptsArchival` and `#recallMemory_returnsMatchingEntries`.

### Tests for User Story 2

- [x] T020 [P] [US2] Add `load_archivalReturnsLatestNOnly` and `load_truncationDoesNotDeleteArchivalEntries` to `LongTermMemoryStoreContractTest.java`
- [x] T021 [P] [US2] Add `recall_searchesArchivalOnly`, `recall_returnsCompleteMultilineEntry`, `recall_treatsKeywordAsLiteralText`, `recall_withoutMatchReturnsEmpty` to `LongTermMemoryStoreContractTest.java`
- [x] T022 [P] [US2] Add `MemoryServiceTest#buildPromptContext_excludesArchivalMemory` and `#recallMemory_delegatesKeyword`
- [x] T023 [US2] Add `MemoryToolsTest.java` with scope-required and recall cases in `oryxos-memory/src/test/java/com/oryxos/memory/MemoryToolsTest.java`

### Implementation for User Story 2

- [x] T024 [P] [US2] Implement `MarkdownMemoryStore.recallByKeyword` searching only ARCHIVAL with literal substring matching in `oryxos-memory/src/main/java/com/oryxos/memory/MarkdownMemoryStore.java`
- [x] T025 [P] [US2] Implement `MemoryService.recallMemory(String)` delegating to backend in `oryxos-memory/src/main/java/com/oryxos/memory/MemoryService.java`
- [x] T026 [US2] Refactor `MemoryTools` so `save_memory` requires explicit scope in `oryxos-memory/src/main/java/com/oryxos/memory/MemoryTools.java`
- [x] T027 [US2] Define multi-line entry boundary format (date header + blank line separator) in `MarkdownMemoryStore.java`
- [x] T028 [US2] Add Markdown-specific contract tests for multi-line entry boundaries in `MarkdownMemoryStoreContractTest.java`
- [x] T029 [US2] Run `mvn test -pl oryxos-memory` and fix failures for US2

**Checkpoint**: User Stories 1 AND 2 both work independently

---

## Phase 5: User Story 3 - Backend can be switched without changing upper layers (Priority: P2)

**Goal**: SQLite and Mem0 backends implement the same `LongTermMemoryStore` contract and are selected by configuration; upper-layer code remains unchanged.

**Independent Test**: Run contract tests for SQLite and Mem0 fake backends; run `MemoryBackendSelectionTest` for default/select/only-one-bean cases.

### Tests for User Story 3

- [x] T030 [P] [US3] Add `SqliteMemoryStoreContractTest` in `oryxos-memory/src/test/java/com/oryxos/memory/SqliteMemoryStoreContractTest.java`
- [x] T031 [P] [US3] Add `Mem0MemoryStoreContractTest` using a fake HTTP server in `oryxos-memory/src/test/java/com/oryxos/memory/Mem0MemoryStoreContractTest.java`
- [x] T032 [P] [US3] Add `MemoryBackendSelectionTest.java` with ApplicationContextRunner in `oryxos-memory/src/test/java/com/oryxos/memory/MemoryBackendSelectionTest.java`
- [x] T033 [P] [US3] Add SQLite-specific tests for migration and stable id ordering in `SqliteMemoryStoreContractTest.java`
- [x] T034 [P] [US3] Add Mem0-specific tests for scope metadata and remote failure in `Mem0MemoryStoreContractTest.java`
- [x] T049 [P] [US3] Create `Mem0MemoryStoreIntegrationTest.java` in `oryxos-memory/src/test/java/com/oryxos/memory/Mem0MemoryStoreIntegrationTest.java` and annotate with `@Tag("integration")`

### Implementation for User Story 3

- [x] T035 [P] [US3] Implement `SqliteMemoryStore` in `oryxos-memory/src/main/java/com/oryxos/memory/SqliteMemoryStore.java`
- [x] T036 [P] [US3] Implement `Mem0MemoryStore` with JDK HttpClient in `oryxos-memory/src/main/java/com/oryxos/memory/Mem0MemoryStore.java`
- [x] T037 [US3] Add conditional bean registration so only one `LongTermMemoryStore` is active in `oryxos-memory/src/main/java/com/oryxos/memory/MemoryStoreConfiguration.java`
- [x] T038 [US3] Set defaults `backend=markdown` and `archival-limit=200` in `MemoryProperties` so `@ConfigurationProperties` binding works without an external YAML file
- [x] T039 [US3] Add `PromptBuilderMemoryIntegrationTest` to verify CORE and history are injected exactly once in `oryxos-memory/src/test/java/com/oryxos/memory/PromptBuilderMemoryIntegrationTest.java`
- [x] T040 [US3] Run `mvn test -pl oryxos-memory` and fix failures for US3

**Checkpoint**: All user stories should now be independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories and final verification

- [x] T041 [P] Run `mvn clean verify` and fix any static analysis, formatting, or test failures across all modules
- [x] T042 [P] Verify H4 global invariants: no caching, no CORE truncation, no default scope, no Spring AI auto tool execution
- [x] T043 Verify no plaintext credentials in configuration; Mem0 api-key uses `${MEM0_API_KEY}` placeholder
- [x] T044 Ensure `Mem0MemoryStoreIntegrationTest` is annotated with `@Tag("integration")` and excluded from the default Maven test run
- [x] T045 [P] Update `docs/dev/day6.md` TODOs if any implementation decisions changed during coding
- [x] T046 Run quickstart.md validation scenarios locally (Markdown backend)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories proceed in priority order (P1 → P2 → P2 backend)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) and ideally after US1 CORE semantics are stable
- **User Story 3 (P2)**: Can start after Foundational (Phase 2) - depends only on the contract test being stable

### Within Each User Story

- Contract tests MUST be written and FAIL before backend implementation
- Models/abstractions before service/backend implementation
- Backend implementation before story-level integration tests
- Story complete before moving to next priority

### Parallel Opportunities

- T003, T004, T005, T006 can run in parallel
- T008, T009, T010 can run in parallel
- T015 (Markdown append/load) and T024 (Markdown recall) can run in parallel after T003-T007
- T035 (SQLite backend) and T036 (Mem0 backend) can run in parallel after contract tests are stable

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1 (Markdown CORE support)
4. **STOP and VALIDATE**: `mvn test -pl oryxos-memory` green for US1

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Markdown CORE works
3. Add User Story 2 → Test independently → ARCHIVAL recall works
4. Add User Story 3 → Test independently → SQLite + Mem0 backends work
5. Polish → full `mvn clean verify`

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify contract tests fail before implementing the backend behavior they assert
- Commit after each user story or logical group
- Stop at any checkpoint to validate the story independently
