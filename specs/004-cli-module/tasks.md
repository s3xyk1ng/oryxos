# Tasks: CLI Module

**Input**: Design documents from `/specs/004-cli-module/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/cli-commands.md, quickstart.md

**Tests**: YES - 8 test classes, 18 acceptance points (explicitly requested in spec)

**Organization**: Tasks grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **CLI module**: `oryxos-cli/src/main/java/com/oryxos/cli/`
- **CLI tests**: `oryxos-cli/src/test/java/com/oryxos/cli/`
- **Core module**: `oryxos-core/src/main/java/com/oryxos/core/`
- **Storage module**: `oryxos-storage/src/main/java/com/oryxos/storage/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify existing dependencies and prepare CLI module structure

- [X] T001 Verify Picocli 4.7.6 dependency exists in oryxos-cli/pom.xml
- [X] T002 [P] Verify Spring Boot starter dependency exists in oryxos-cli/pom.xml
- [X] T003 [P] Verify sessions table migration script exists in oryxos-storage/src/main/resources/db/migration/sqlite/V1__init_schema.sql
- [X] T004 Create package structure: oryxos-cli/src/main/java/com/oryxos/cli/command/subcommand/

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T005 Implement SessionManager (memory version) in oryxos-core/src/main/java/com/oryxos/core/agent/SessionManager.java with ConcurrentHashMap storage and generateSessionId() using SHA-256(channel + ":" + userId + ":" + profileName)
- [X] T006 [P] Implement StartupVerifier in oryxos-cli/src/main/java/com/oryxos/cli/StartupVerifier.java with three-step verification: Bean assertion, data access layer read/write check
- [X] T007 [P] Implement OryxOsCli main entry point in oryxos-cli/src/main/java/com/oryxos/cli/OryxOsCli.java with light/heavy command routing based on LIGHT_COMMANDS whitelist
- [X] T008 Create SessionManagerTest in oryxos-cli/src/test/java/com/oryxos/cli/SessionManagerTest.java with 2 acceptance points: idempotency (same triple returns same Session), uniqueness (different triples return different Sessions)

**Checkpoint**: Foundation ready - SessionManager, StartupVerifier, OryxOsCli routing, and SessionManagerTest complete

---

## Phase 3: User Story 1 - Profile Management (Priority: P1) 🎯 MVP

**Goal**: Enable users to create, list, show, and delete Agent Profiles via CLI commands

**Independent Test**: Run `oryxos profile list/create/show/delete` commands and verify file operations

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T009 [P] [US1] Create ProfileCommandTest in oryxos-cli/src/test/java/com/oryxos/cli/command/ProfileCommandTest.java with 4 acceptance points: list (scan directory), create (generate template), show (read AGENT.md), delete (remove directory)

### Implementation for User Story 1

- [X] T010 [P] [US1] Create ProfileCommand (command group) in oryxos-cli/src/main/java/com/oryxos/cli/command/ProfileCommand.java with @Command annotation declaring subcommands: ProfileListCommand, ProfileCreateCommand, ProfileShowCommand, ProfileDeleteCommand
- [X] T011 [P] [US1] Create ProfileListCommand in oryxos-cli/src/main/java/com/oryxos/cli/command/subcommand/ProfileListCommand.java that scans .oryxos/agents/ directory and prints Agent names
- [X] T012 [P] [US1] Create ProfileCreateCommand in oryxos-cli/src/main/java/com/oryxos/cli/command/subcommand/ProfileCreateCommand.java that creates .oryxos/agents/<NAME>/AGENT.md template with frontmatter (name, description, provider, model, max_iterations, max_history_turns)
- [X] T013 [P] [US1] Create ProfileShowCommand in oryxos-cli/src/main/java/com/oryxos/cli/command/subcommand/ProfileShowCommand.java that reads and displays AGENT.md content
- [X] T014 [P] [US1] Create ProfileDeleteCommand in oryxos-cli/src/main/java/com/oryxos/cli/command/subcommand/ProfileDeleteCommand.java that deletes .oryxos/agents/<NAME>/ directory with confirmation prompt
- [X] T015 [US1] Add idempotency check to ProfileCreateCommand: if .oryxos/agents/<NAME>/ already exists, print "Profile already exists: <NAME>" and exit without overwriting

**Checkpoint**: Profile CRUD commands work independently, ProfileCommandTest passes (4/4 acceptance points)

---

## Phase 4: User Story 2 - System Queries (Priority: P2)

**Goal**: Enable users to query system status, configured Providers, and available Tools

**Independent Test**: Run `oryxos status`, `oryxos provider list`, `oryxos tool list` and verify output

### Tests for User Story 2 ⚠️

- [X] T016 [P] [US2] Create StatusCommandTest in oryxos-cli/src/test/java/com/oryxos/cli/command/StatusCommandTest.java with 1 acceptance point: status output shows workspace info, Agent count, Provider count
- [X] T017 [P] [US2] Create ProviderCommandTest in oryxos-cli/src/test/java/com/oryxos/cli/command/ProviderCommandTest.java with 2 acceptance points: provider list (read application.yml), empty provider handling
- [X] T018 [P] [US2] Create ToolCommandTest in oryxos-cli/src/test/java/com/oryxos/cli/command/ToolCommandTest.java with 2 acceptance points: tool list (scan .oryxos/tools/), empty tool handling

### Implementation for User Story 2

- [X] T019 [P] [US2] Create StatusCommand in oryxos-cli/src/main/java/com/oryxos/cli/command/StatusCommand.java that reads .oryxos/config/application.yml, counts Agents/Providers/Tools, and prints formatted status
- [X] T020 [P] [US2] Create ProviderCommand (command group) in oryxos-cli/src/main/java/com/oryxos/cli/command/ProviderCommand.java with @Command annotation declaring subcommand: ProviderListCommand
- [X] T021 [P] [US2] Create ProviderListCommand in oryxos-cli/src/main/java/com/oryxos/cli/command/subcommand/ProviderListCommand.java that reads application.yml and extracts provider configurations
- [X] T022 [P] [US2] Create ToolCommand (command group) in oryxos-cli/src/main/java/com/oryxos/cli/command/ToolCommand.java with @Command annotation declaring subcommand: ToolListCommand
- [X] T023 [P] [US2] Create ToolListCommand in oryxos-cli/src/main/java/com/oryxos/cli/command/subcommand/ToolListCommand.java that scans .oryxos/tools/ directory for tool definitions

**Checkpoint**: System query commands work independently, StatusCommandTest (1/1), ProviderCommandTest (2/2), ToolCommandTest (2/2) pass

---

## Phase 5: User Story 3 - Workspace Initialization (Priority: P3)

**Goal**: Enable users to initialize OryxOS workspace with default directory structure

**Independent Test**: Run `oryxos init` twice and verify idempotency

### Tests for User Story 3 ⚠️

- [X] T024 [P] [US3] Create InitCommandTest in oryxos-cli/src/test/java/com/oryxos/cli/command/InitCommandTest.java with 2 acceptance points: init creates .oryxos/ directory structure, idempotency (running twice does not overwrite)

### Implementation for User Story 3

- [X] T025 [P] [US3] Create InitCommand in oryxos-cli/src/main/java/com/oryxos/cli/command/InitCommand.java that creates .oryxos/agents/, .oryxos/tools/, .oryxos/config/ directories and default application.yml
- [X] T026 [US3] Add idempotency check to InitCommand: if .oryxos/ already exists, print "Workspace already initialized." and exit without overwriting existing files

**Checkpoint**: Init command works independently, InitCommandTest passes (2/2 acceptance points)

---

## Phase 6: User Story 4 - Interactive Chat (Priority: P1) 🎯 MVP

**Goal**: Enable users to have interactive multi-turn conversations with an Agent via CLI

**Independent Test**: Run `oryxos chat weather`, send messages, verify responses, input /quit to exit

**Dependencies**: Requires User Story 1 (Profile Management) to load Agents

### Tests for User Story 4 ⚠️

- [X] T027 [P] [US4] Create ChatCommandTest in oryxos-cli/src/test/java/com/oryxos/cli/command/ChatCommandTest.java with 4 acceptance points: /quit exits loop, empty input skipped (no Agent call), exception handling (print "Error: {message}", continue loop), normal call (invoke AgentService.process() and print result)

### Implementation for User Story 4

- [X] T028 [US4] Create ChatCommand in oryxos-cli/src/main/java/com/oryxos/cli/command/ChatCommand.java that implements interactive loop: load Agents → resolve Profile → create Session → read stdin → empty? skip → /quit? exit → AgentService.process() → print result → loop
- [X] T029 [US4] Add empty input handling to ChatCommand: if input.trim().isEmpty(), skip and re-display "> " prompt
- [X] T030 [US4] Add exception handling to ChatCommand: catch Exception from AgentService.process(), print "Error: {message}", do not print stack trace, continue loop
- [X] T031 [US4] Add /quit exit condition to ChatCommand: if input equals "/quit", break loop and exit
- [X] T032 [US4] Add default user identity to ChatCommand: use channel="cli", userId="cli-user" (no username prompt)

**Checkpoint**: Chat command works independently with AgentService mock, ChatCommandTest passes (4/4 acceptance points)

---

## Phase 7: User Story 5 - Service Startup Skeleton (Priority: P3)

**Goal**: Provide skeleton commands for Web Service and Gateway daemon startup

**Independent Test**: Run `oryxos serve` and `oryxos gateway`, verify Spring startup and StartupVerifier execution

**Dependencies**: Requires Phase 2 (StartupVerifier, OryxOsCli routing)

### Tests for User Story 5

> **NOTE: No dedicated test class for serve/gateway in core stage (skeleton only)**

### Implementation for User Story 5

- [X] T033 [P] [US5] Create ServeCommand in oryxos-cli/src/main/java/com/oryxos/cli/command/ServeCommand.java that calls StartupVerifier.verify() and prints "OryxOS Web Service started"
- [X] T034 [P] [US5] Create GatewayCommand in oryxos-cli/src/main/java/com/oryxos/cli/command/GatewayCommand.java that calls StartupVerifier.verify() and prints "OryxOS gateway started"

**Checkpoint**: Serve/Gateway commands start Spring and pass verification

---

## Phase 8: User Story 6 - Session Persistence Test (Priority: P2)

**Goal**: Verify that SessionEntity and SessionRepository can persist messages_json across restarts (integration test only)

**Independent Test**: Run @Tag("integration") test to verify SQLite persistence

**Dependencies**: Requires existing SessionEntity and SessionRepository in oryxos-storage

### Tests for User Story 6 ⚠️

- [X] T035 [P] [US6] [Tag("integration")] Create SessionRepositoryTest in oryxos-storage/src/test/java/com/oryxos/storage/repository/SessionRepositoryTest.java with 1 acceptance point: messages_json persistence across restarts (save Session, create new ApplicationContext, reload Session, verify messages_json unchanged)

### Implementation for User Story 6

> **NOTE: No implementation needed - SessionEntity and SessionRepository already exist from Day 2. This is integration test only.**

**Checkpoint**: SessionRepositoryTest passes (1/1 acceptance point)

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [X] T036 [P] Add StartupVerifierTest in oryxos-cli/src/test/java/com/oryxos/cli/StartupVerifierTest.java to verify three-step verification (Bean assertion, data access layer check)
- [X] T037 [P] Update OryxOsCli.java to register all 11 commands: chat, serve, gateway, init, status, profile (with 4 subcommands), provider (with 1 subcommand), tool (with 1 subcommand)
- [X] T038 Run quickstart.md validation scenarios 1-13 manually
- [X] T039 Run full test suite: `mvn test -pl oryxos-cli,oryxos-storage`
- [X] T040 Run full build with quality gates: `mvn clean verify`
- [X] T041 Verify all 8 test classes exist and are non-empty
- [X] T042 Verify all 18 acceptance points pass (count tests across 8 test classes)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-8)**: All depend on Foundational phase completion
  - US1 (Profile Management): Can start after Phase 2 - No dependencies
  - US2 (System Queries): Can start after Phase 2 - No dependencies
  - US3 (Workspace Init): Can start after Phase 2 - No dependencies
  - US4 (Interactive Chat): Can start after Phase 2 + US1 (needs Profile loading)
  - US5 (Service Startup): Can start after Phase 2 - No dependencies
  - US6 (Session Persistence): Can start after Phase 2 - No dependencies (test only)
- **Polish (Phase 9)**: Depends on all user stories being complete

### User Story Dependencies

- **US1 (Profile Management)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **US2 (System Queries)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **US3 (Workspace Init)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **US4 (Interactive Chat)**: Can start after Foundational (Phase 2) - Depends on US1 for Profile loading
- **US5 (Service Startup)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **US6 (Session Persistence)**: Can start after Foundational (Phase 2) - No dependencies (test only)

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- Command groups before subcommands
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel (T002, T003)
- All Foundational tasks marked [P] can run in parallel (T006, T007)
- Once Foundational phase completes, US1/US2/US3/US5/US6 can start in parallel
- US4 must wait for US1 (Profile loading)
- All tests for a user story marked [P] can run in parallel
- All subcommands within a story marked [P] can run in parallel

---

## Parallel Example: User Story 1 (Profile Management)

```bash
# Launch test first:
Task T009: "Create ProfileCommandTest in oryxos-cli/src/test/java/com/oryxos/cli/command/ProfileCommandTest.java"

# Then launch all subcommands in parallel:
Task T010: "Create ProfileCommand (command group)"
Task T011: "Create ProfileListCommand"
Task T012: "Create ProfileCreateCommand"
Task T013: "Create ProfileShowCommand"
Task T014: "Create ProfileDeleteCommand"

# Then add idempotency:
Task T015: "Add idempotency check to ProfileCreateCommand"
```

---

## Implementation Strategy

### MVP First (US1 + US4 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: US1 (Profile Management)
4. Complete Phase 6: US4 (Interactive Chat)
5. **STOP and VALIDATE**: Test Profile CRUD + Chat independently
6. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add US1 (Profile Management) → Test independently → Deploy/Demo (MVP!)
3. Add US2 (System Queries) → Test independently → Deploy/Demo
4. Add US3 (Workspace Init) → Test independently → Deploy/Demo
5. Add US4 (Interactive Chat) → Test independently → Deploy/Demo
6. Add US5 (Service Startup) → Test independently → Deploy/Demo
7. Add US6 (Session Persistence) → Test independently → Deploy/Demo
8. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: US1 (Profile Management) → US4 (Chat)
   - Developer B: US2 (System Queries) → US3 (Workspace Init)
   - Developer C: US5 (Service Startup) → US6 (Session Persistence Test)
3. Stories complete and integrate independently

---

## Test Summary

| Test Class | Acceptance Points | Story | Phase |
|------------|-------------------|-------|-------|
| SessionManagerTest | 2 | Foundational | Phase 2 |
| ProfileCommandTest | 4 | US1 | Phase 3 |
| StatusCommandTest | 1 | US2 | Phase 4 |
| ProviderCommandTest | 2 | US2 | Phase 4 |
| ToolCommandTest | 2 | US2 | Phase 4 |
| InitCommandTest | 2 | US3 | Phase 5 |
| ChatCommandTest | 4 | US4 | Phase 6 |
| SessionRepositoryTest | 1 | US6 | Phase 8 |
| **Total** | **18** | | |

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Test methods MUST be in English (camelCase or snake_case), use @DisplayName for Chinese descriptions
- Avoid P3C/ASM-incompatible Java 18+ syntax (e.g., enhanced switch default ->)
