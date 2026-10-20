-- Day 2: ReAct 模块审计增强，tool_invocations 增加 tool_call_id 列，关联 LLM 返回的 tool_call id
ALTER TABLE tool_invocations ADD COLUMN tool_call_id VARCHAR(64);
