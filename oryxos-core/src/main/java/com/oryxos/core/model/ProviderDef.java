package com.oryxos.core.model;

import java.util.List;

/**
 * Provider 连接定义。由全局配置（application.yaml → oryxos.providers[]）注册， 每个 ProviderDef 代表一个可用的 LLM 连接资源。
 */
public record ProviderDef(
        String name, String type, String apiKey, String baseUrl, List<String> fallbacks) {

    /** 便捷构造（无 fallbacks、无 baseUrl）。 */
    public ProviderDef(String name, String type, String apiKey) {
        this(name, type, apiKey, null, List.of());
    }
}
