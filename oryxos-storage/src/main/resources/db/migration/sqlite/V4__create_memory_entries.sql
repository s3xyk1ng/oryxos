CREATE TABLE IF NOT EXISTS memory_entries (
    id          INTEGER      PRIMARY KEY AUTOINCREMENT,
    scope       VARCHAR(16)  NOT NULL
                CHECK (scope IN ('CORE', 'ARCHIVAL')),
    content     TEXT         NOT NULL
                CHECK (length(trim(content)) > 0),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_memory_entries_scope_id
    ON memory_entries(scope, id);
