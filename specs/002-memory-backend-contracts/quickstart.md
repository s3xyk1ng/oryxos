# Quickstart: Memory Backend Contracts

## Prerequisites

- JDK 21
- Maven
- This branch checked out: `006-day6-memory-refactor`
- For real Mem0 validation: a self-hosted Mem0 instance and environment variables `MEM0_BASE_URL`, `MEM0_API_KEY`
- For real model validation: a configured LLM provider and API key

## Build

```bash
mvn install -DskipTests
```

## Run contract tests

```bash
mvn test -pl oryxos-memory
```

Expected result: all default tests pass, including Markdown, SQLite, and Mem0-fake contract tests.

## Switch backends locally

### Markdown (default)

```yaml
oryxos:
  memory:
    backend: markdown
    archival-limit: 200
    markdown:
      path: .oryxos/memory/MEMORY.md
```

### SQLite

```yaml
oryxos:
  memory:
    backend: sqlite
    archival-limit: 200
```

### Mem0

```yaml
oryxos:
  memory:
    backend: mem0
    archival-limit: 200
    mem0:
      base-url: ${MEM0_BASE_URL}
      api-key: ${MEM0_API_KEY}
```

## Manual validation scenarios

### Scenario 1: CORE is always visible

1. Start the application with `backend: markdown`.
2. Ask the Agent to save a project codename as CORE.
3. In the next turn, ask "What is the project codename?" without mentioning it.
4. Verify the answer is correct and `MEMORY.md` contains the codename under `## 核心记忆`.

### Scenario 2: ARCHIVAL is recalled on demand

1. Ask the Agent to save a discussion note as ARCHIVAL.
2. Start a new turn and ask a question unrelated to that note.
3. Verify the note does not appear in the answer.
4. Ask "Please recall the discussion note about X."
5. Verify the note is returned.

### Scenario 3: Backend switch without migration

1. Save a memory with `backend: markdown`.
2. Stop the application, change to `backend: sqlite`, and start again.
3. Save a different memory.
4. Verify `MEMORY.md` is not modified and the SQLite database contains only the second memory.

### Scenario 4: Real Mem0 integration

1. Configure `MEM0_BASE_URL` and `MEM0_API_KEY`.
2. Run:
   ```bash
   mvn test -pl oryxos-memory -Dtest=Mem0MemoryStoreIntegrationTest -Dgroups=integration
   ```
3. Verify writes, restart-load, ARCHIVAL recall, and failure handling all pass.

### Scenario 5: Real model ReAct

1. Configure a real provider.
2. Run a conversation:
   - Turn 1: "Remember that project codename ORYXOS-E2E-<timestamp> is CORE."
   - Turn 2: "Remember that validation note ORYXOS-E2E-<timestamp>-note is ARCHIVAL."
   - Turn 3 (new session): "What is the project codename?" → should answer correctly.
   - Turn 4: "Recall the validation note." → should return the note.
3. Verify `tool_invocations` and `llm_calls` contain audit rows.

## Expected full build

```bash
mvn clean verify
```

All 15 modules BUILD SUCCESS, default tests 0 failures / 0 errors.
