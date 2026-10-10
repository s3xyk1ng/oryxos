-- V3: Create scheduled_tasks and task_executions tables
CREATE TABLE IF NOT EXISTS scheduled_tasks (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    task_key     VARCHAR(128) NOT NULL UNIQUE,
    profile_name VARCHAR(64)  NOT NULL,
    cron         VARCHAR(64)  NOT NULL,
    zone         VARCHAR(64)  DEFAULT 'Asia/Shanghai',
    message      TEXT,
    display_name VARCHAR(128),
    enabled      BOOLEAN      NOT NULL DEFAULT 1,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP
);

CREATE TABLE IF NOT EXISTS task_executions (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    task_key    VARCHAR(128) NOT NULL,
    session_id  VARCHAR(64),
    status      VARCHAR(16)  NOT NULL,
    result      TEXT,
    started_at  TIMESTAMP    NOT NULL,
    finished_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_scheduled_tasks_key ON scheduled_tasks(task_key);
CREATE INDEX IF NOT EXISTS idx_task_executions_key ON task_executions(task_key);
