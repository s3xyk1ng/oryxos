-- V1__init_schema.sql
-- OryxOS core schema: sessions + tool_invocations + llm_calls

CREATE TABLE IF NOT EXISTS sessions (
    session_id    VARCHAR(64)  PRIMARY KEY,
    profile_name  VARCHAR(64)  NOT NULL,
    channel       VARCHAR(32)  NOT NULL,
    user_id       VARCHAR(64)  NOT NULL,
    messages_json TEXT         NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'active',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at   TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tool_invocations (
    id            BIGINT       PRIMARY KEY AUTOINCREMENT,
    session_id    VARCHAR(64)  NOT NULL,
    tool_name     VARCHAR(64)  NOT NULL,
    input_json    TEXT         NOT NULL,
    result_json   TEXT         NOT NULL,
    success       BOOLEAN      NOT NULL,
    error_message TEXT,
    duration_ms   BIGINT       NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS llm_calls (
    id                 BIGINT       PRIMARY KEY AUTOINCREMENT,
    session_id         VARCHAR(64)  NOT NULL,
    provider           VARCHAR(32)  NOT NULL,
    model              VARCHAR(64)  NOT NULL,
    prompt_tokens      INT          NOT NULL,
    completion_tokens  INT          NOT NULL,
    total_tokens       INT          NOT NULL,
    duration_ms        BIGINT       NOT NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tool_invocations_session ON tool_invocations(session_id);
CREATE INDEX IF NOT EXISTS idx_llm_calls_session ON llm_calls(session_id);
CREATE INDEX IF NOT EXISTS idx_sessions_status ON sessions(status);
