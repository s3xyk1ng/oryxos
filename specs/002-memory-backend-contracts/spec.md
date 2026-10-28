# Feature Specification: Memory Backend Contracts

**Feature Branch**: `006-day6-memory-refactor`

**Created**: 2026-10-27

**Status**: Draft

**Input**: User description: "第6天需求（本地编号）：Memory 模块——统一门面 + 三档后端契约"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - CORE memory is always visible in prompt (Priority: P1)

A user tells the Agent to remember a project codename as something that should always be available. The Agent explicitly marks it as CORE memory. In every subsequent turn, the prompt automatically includes this codename so the Agent can answer questions about it without being asked again.

**Why this priority**: This is the foundational value of long-term memory — stable facts survive across sessions and turns.

**Independent Test**: Save a CORE memory through the Agent tool, then build a prompt context and verify the CORE content appears exactly once.

**Acceptance Scenarios**:

1. **Given** no CORE memory exists, **When** the Agent saves a project codename as CORE, **Then** the next prompt context includes the codename.
2. **Given** multiple CORE memories exist, **When** the prompt is assembled, **Then** all CORE memories are included completely without truncation.

---

### User Story 2 - ARCHIVAL memory is recalled on demand (Priority: P2)

A user lets the Agent store routine discussion notes as ARCHIVAL memory. These notes do not clutter every prompt. Later, the user asks the Agent to recall notes about a specific topic, and the Agent retrieves only the matching ARCHIVAL entries.

**Why this priority**: Keeps the prompt focused while preserving a searchable history of less-critical information.

**Independent Test**: Save an ARCHIVAL memory, confirm it does not appear in the automatic prompt context, then call recall with a matching keyword and verify the entry is returned.

**Acceptance Scenarios**:

1. **Given** an ARCHIVAL memory exists, **When** the prompt context is built, **Then** the ARCHIVAL content is not automatically injected.
2. **Given** an ARCHIVAL memory contains a keyword, **When** the Agent recalls that keyword, **Then** the full matching entry is returned.
3. **Given** a keyword matches only CORE memory, **When** the Agent recalls that keyword, **Then** no CORE memory is returned.

---

### User Story 3 - Backend can be switched without changing upper layers (Priority: P2)

A deployment team starts with the default file-based backend. As the memory volume grows, they switch to a structured local database backend by changing one configuration line. Later, they may switch to a self-hosted external memory service. In all cases, the Agent-facing behavior and the ReAct loop code remain unchanged.

**Why this priority**: Backend portability is the architectural goal that lets different deployments choose the right trade-off between simplicity and scale.

**Independent Test**: Run the same contract tests against each supported backend implementation and verify identical behavior for append, load, and recall.

**Acceptance Scenarios**:

1. **Given** the default backend is active, **When** CORE and ARCHIVAL memories are saved, **Then** they are persisted and retrievable.
2. **Given** the structured database backend is active, **When** the same operations are performed, **Then** the results match the default backend behavior.
3. **Given** the external memory backend is active, **When** the same operations are performed, **Then** the results match the default backend behavior.
4. **Given** a backend switch, **When** the application restarts, **Then** data from the previous backend is not mixed into the new backend.

---

### Edge Cases

- What happens when the Agent tries to save empty content? The system rejects it and nothing is written.
- What happens when the Agent omits the scope on save? The system rejects the call and does not default to ARCHIVAL.
- What happens when a keyword contains characters that look like pattern syntax (%, _, .* , [a-z])? They are treated as ordinary text, not as wildcards or regular expressions.
- What happens when a backend fails to read? The failure is reported as an error, not disguised as "no memories found."
- What happens when ARCHIVAL entries exceed the configured window? Only the newest entries are loaded automatically, but older entries remain stored and can still be recalled by keyword.
- What happens when a multi-line memory has the keyword on its second line? The entire multi-line memory is returned, not just the matching line.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The memory facade MUST expose only three upper-layer capabilities: prompt-context assembly, saving a memory, and recalling archival memories by keyword.
- **FR-002**: Prompt-context assembly MUST include the complete CORE memory and the current session conversation history, and MUST NOT include ARCHIVAL memory.
- **FR-003**: The long-term memory backend interface MUST expose only three operations: append content to a scope, load persisted memory, and recall entries by keyword.
- **FR-004**: The scope MUST have exactly two values: CORE and ARCHIVAL. No automatic, temporary, or inferred scopes are allowed.
- **FR-005**: The Agent MUST explicitly choose CORE or ARCHIVAL on every save. The system MUST NOT guess or default the scope.
- **FR-006**: All backend implementations MUST reload from real storage on every load operation; no local caching that would hide recently written memories is allowed.
- **FR-007**: CORE memory MUST never be truncated. Truncation, if any, MUST apply only to ARCHIVAL loading windows.
- **FR-008**: Recall MUST search only ARCHIVAL memory. CORE memory MUST NOT be returned by recall.
- **FR-009**: Recall MUST perform simple keyword containment matching. Regular expressions, tokenization, stemming, and semantic expansion MUST NOT be used in the initial version.
- **FR-010**: Special characters in recall keywords (%, _, .*, [a-z], etc.) MUST be treated as literal text.
- **FR-011**: Recall MUST return complete memory entries. A keyword matching one line of a multi-line entry MUST return the whole entry.
- **FR-012**: Only one backend implementation MUST be active at a time. Backend selection MUST be a configuration switch with no data migration between backends.
- **FR-013**: The save-memory tool schema and implementation MUST require an explicit scope parameter.
- **FR-014**: A backend failure during load or recall MUST be distinguishable from an empty result.

### Key Entities *(include if feature involves data)*

- **Memory Scope**: A classification with exactly two values — CORE (always visible) and ARCHIVAL (recalled on demand).
- **Memory Entry**: A single stored memory consisting of content and a scope. Multi-line content is kept as one entry.
- **Memory Content**: The result of loading memory, separated into CORE and ARCHIVAL sections so callers can decide how to use each section.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Saving a CORE memory makes it visible in the next prompt context with zero additional Agent action.
- **SC-002**: Saving an ARCHIVAL memory does not increase prompt size until the Agent explicitly recalls it.
- **SC-003**: The same contract test suite passes against all three backend implementations without assertion changes.
- **SC-004**: Switching the active backend via configuration does not require changes to the ReAct loop, tool definitions, or prompt builder logic.
- **SC-005**: 100% of save attempts without an explicit scope are rejected rather than defaulted.
- **SC-006**: A backend failure produces an observable error, and 0% of backend failures are silently converted to empty results.

## Assumptions

- The Agent (LLM) is responsible for deciding whether a piece of information is CORE or ARCHIVAL; the system does not auto-classify.
- Session conversation history is managed outside the long-term memory module and is only appended to the prompt context, not written back as long-term memory.
- The initial recall behavior is intentionally simple (substring containment) and may be enhanced later without relaxing the ARCHIVAL-only constraint.
- Data migration between backends is out of scope; switching backends starts with an empty store on the new backend.
- The external memory backend is self-hosted; cloud-hosted or managed external services are out of scope for the initial implementation.
