package com.oryxos.provider;

import com.oryxos.core.model.ProviderDef;
import java.util.ArrayList;
import java.util.List;

/**
 * 绑定 application.yaml 中 oryxos.providers[] 配置。
 *
 * <pre>
 * oryxos:
 *   providers:
 *     - name: qwen-plus
 *       type: qwen
 *       apiKey: ${DASHSCOPE_API_KEY}
 *       baseUrl: https://dashscope.aliyuncs.com/compatible-mode/v1
 * </pre>
 */
public class ProvidersProperties {

    private List<ProviderConfig> providers = new ArrayList<>();

    public List<ProviderConfig> getProviders() {
        return providers;
    }

    public void setProviders(List<ProviderConfig> providers) {
        this.providers = providers;
    }

    /** 将配置转为 ProviderDef。 */
    public List<ProviderDef> toProviderDefs() {
        return providers.stream()
                .map(
                        c ->
                                new ProviderDef(
                                        c.getName(),
                                        c.getType(),
                                        c.getApiKey(),
                                        c.getBaseUrl(),
                                        c.getFallbacks()))
                .toList();
    }

    /** 单个 Provider 配置项。 */
    public static class ProviderConfig {
        private String name;
        private String type;
        private String apiKey;
        private String baseUrl;
        private List<String> fallbacks;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public List<String> getFallbacks() {
            return fallbacks;
        }

        public void setFallbacks(List<String> fallbacks) {
            this.fallbacks = fallbacks;
        }
    }
}
