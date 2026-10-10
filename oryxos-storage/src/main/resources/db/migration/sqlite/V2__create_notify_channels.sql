-- V2: Create notify_channels table for notification channel registry
CREATE TABLE IF NOT EXISTS notify_channels (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        VARCHAR(64)  NOT NULL UNIQUE,
    type        VARCHAR(32)  NOT NULL,
    url         VARCHAR(512) NOT NULL,
    config_json TEXT,
    description VARCHAR(256),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notify_channels_name ON notify_channels(name);
CREATE INDEX IF NOT EXISTS idx_notify_channels_type ON notify_channels(type);
