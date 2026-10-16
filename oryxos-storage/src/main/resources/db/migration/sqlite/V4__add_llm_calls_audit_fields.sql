-- V4__add_llm_calls_audit_fields.sql
-- Add audit fields to llm_calls table (Day 1 Provider module)

ALTER TABLE llm_calls ADD COLUMN agent_name     VARCHAR(64)  NOT NULL DEFAULT 'unknown';
ALTER TABLE llm_calls ADD COLUMN status         VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS';
ALTER TABLE llm_calls ADD COLUMN tool_call_count INT          NOT NULL DEFAULT 0;
ALTER TABLE llm_calls ADD COLUMN error_message   VARCHAR(1024);
