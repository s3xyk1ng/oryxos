# Data Model: Memory Backend Contracts

## Entity: Memory Entry

A single stored memory. Each row represents one complete memory entry, which may contain multi-line text.

| Field | Type | Nullable | Constraints | Description |
|---|---|---|---|---|
| `id` | INTEGER | No | PRIMARY KEY AUTOINCREMENT | Stable insertion order; used to determine newness when timestamps may coincide. |
| `scope` | VARCHAR(16) | No | CHECK (scope IN ('CORE', 'ARCHIVAL')) | Partition: CORE is always loaded completely; ARCHIVAL is loaded through a window and searched. |
| `content` | TEXT | No | CHECK (length(trim(content)) > 0) | The full memory text; multi-line content is preserved as-is. |
| `created_at` | TIMESTAMP | No | DEFAULT CURRENT_TIMESTAMP | Human-readable timestamp; ordering decisions use `id` for stability. |

## Indexes

```text
idx_memory_entries_scope_id ON memory_entries(scope, id)
```

Supports both full CORE load ordered by `id ASC` and ARCHIVAL window load ordered by `id DESC LIMIT N`.

## Relationships

- No foreign keys to sessions or users in the initial version. The backend is a flat append-only store per deployment.
- Session history remains managed by `SessionManager` and the `sessions` / `messages` tables.

## Validation Rules

- `scope` must be exactly `CORE` or `ARCHIVAL`.
- `content` must be non-empty after trimming whitespace.
- Entries are append-only; there is no update or delete operation in the initial contract.
