package com.oryxos.core.model;

import java.util.List;

/** LLM 调用请求。messages 为对话消息列表，tools 为当前可用工具列表。 */
public record Prompt(List<Message> messages, List<OryxTool> tools) {}
