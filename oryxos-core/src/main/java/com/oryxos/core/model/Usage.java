package com.oryxos.core.model;

/** Token 用量统计。 */
public record Usage(long promptTokens, long completionTokens, long totalTokens) {}
