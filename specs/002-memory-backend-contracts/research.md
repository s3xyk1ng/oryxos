# Research: Memory Backend Contracts

## Decisions

### Markdown multi-line entry boundary

**Decision**: Each entry in `MEMORY.md` is written with a date header followed by the content on the next line(s); entries are separated by a blank line. Parsing stops at the next date header or end of section.

**Rationale**: This matches the existing `MEMORY.md` structure shown in `TechnicalSolution.md` §5.2, keeps the file human-readable, and allows a multi-line memory to be returned as a single unit.

**Alternatives considered**:
- One-line-per-entry: rejected because it cannot represent multi-line memories.
- YAML frontmatter per entry: rejected because it increases file complexity and is not required for the initial keyword-recall contract.

### Initial Mem0 keyword recall strategy

**Decision**: For the Mem0 backend, `recallByKeyword` first calls Mem0 search with the keyword, then filters the returned candidates locally: discard entries whose metadata scope is not `ARCHIVAL`, and keep only those whose content contains the keyword as literal text.

**Rationale**: Mem0's native search may be semantic; local filtering guarantees the contract "recall only searches ARCHIVAL" and "simple keyword containment" are honored regardless of Mem0 version. This avoids enabling semantic expansion in the initial version.

**Alternatives considered**:
- Rely entirely on Mem0 metadata filters: rejected because filter syntax varies by Mem0 version and may not guarantee literal containment.
- Fetch all ARCHIVAL entries and scan locally: rejected because it defeats the purpose of an external search service and may be slow at scale.

### SQLite recall literal-text matching

**Decision**: Use `instr(lower(content), lower(:keyword)) > 0` for SQLite recall.

**Rationale**: `instr()` treats `%`, `_`, `.*`, `[a-z]` as ordinary characters and performs case-insensitive substring matching without LIKE or regex semantics.

**Alternatives considered**:
- `LIKE '%keyword%'` with escaped wildcards: rejected because escaping is error-prone and the behavior is harder to verify across drivers.
- Full-text search (FTS): rejected because it introduces tokenization and is out of scope for the initial keyword-containment contract.

### MemoryTools registration form

**Decision**: Keep `MemoryTools` as a Spring `@Component` exposing inner `OryxTool` implementations for `save_memory` and `recall_memory`. Migrate to `@Tool` annotation only if Day 5's `@Tool` infrastructure is confirmed available on this branch.

**Rationale**: The behavior contract is independent of the registration mechanism. Inner-class `OryxTool` is already present and known to work; `@Tool` migration is a non-behavioral cleanup.

**Alternatives considered**:
- Always use `@Tool`: rejected because this branch may not yet contain Day 5's annotation scanner, and the contract tests must pass regardless.

## Unknowns deferred to implementation

- Exact Mem0 API version, endpoint paths, pagination parameters, and metadata filter syntax will be determined against the actual self-hosted Mem0 instance during integration testing.
