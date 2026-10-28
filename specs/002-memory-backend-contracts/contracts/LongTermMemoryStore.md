# Interface Contract: LongTermMemoryStore

## Purpose

`LongTermMemoryStore` is the pluggable backend contract for long-term memory. All backend implementations (Markdown, SQLite, Mem0) must expose exactly these three operations and satisfy the invariants below.

## Operations

### `void append(String content, MemoryScope scope)`

**Preconditions**:
- `content` is non-null and non-blank after trimming.
- `scope` is non-null and is either `CORE` or `ARCHIVAL`.

**Postconditions**:
- The content is persisted under the given scope.
- A subsequent `load()` or `recallByKeyword(...)` performed through any store instance connected to the same backend must observe the newly persisted entry immediately.

**Failure modes**:
- Illegal argument (blank content or null/invalid scope) → `IllegalArgumentException`.
- Backend failure (disk full, DB unavailable, network error) → `MemoryStoreException` or other runtime exception; must not be silently ignored.

### `MemoryContent load()`

**Returns**:
- A `MemoryContent` object separating CORE and ARCHIVAL sections.
- CORE section contains every persisted CORE entry, in insertion order, completely untruncated.
- ARCHIVAL section contains the most recent N entries (where N is the configured archival window), in reverse insertion order (newest first) or restored insertion order as documented by the implementation.
- Empty stores return a `MemoryContent` with empty strings, never `null`.

**Invariants**:
- Every call re-reads from real storage; no caching.
- CORE entries are never omitted, truncated, or limited.
- Old ARCHIVAL entries beyond the window remain persisted and can still be recalled by keyword.

**Failure modes**:
- Backend failure → exception; must not return empty content.

### `List<String> recallByKeyword(String keyword)`

**Preconditions**:
- `keyword` is non-null and non-blank.

**Returns**:
- A list of complete ARCHIVAL entries whose content contains the keyword as literal text.
- Matching is case-insensitive.
- Special characters (`%`, `_`, `.*`, `[a-z]`, etc.) are treated as ordinary characters, not as wildcards or regex.
- If a keyword matches only a single line of a multi-line entry, the entire entry is returned.
- CORE entries are never returned.
- No match returns an empty list.

**Invariants**:
- Every call re-reads from real storage; no caching.
- Search is restricted to ARCHIVAL scope only.
- Matching is simple substring containment; no tokenization, stemming, or semantic expansion.

**Failure modes**:
- Backend failure → exception; must not return an empty list.

## Cross-cutting invariants

1. **No caching**: Implementations must not keep a local snapshot that would make recently written entries invisible.
2. **CORE never truncated or searched**: Truncation and keyword recall apply only to ARCHIVAL.
3. **Agent-selected scope is preserved**: The backend must not remap, default, or infer scope.
4. **Keyword literal matching**: Recall uses ordinary substring containment.
