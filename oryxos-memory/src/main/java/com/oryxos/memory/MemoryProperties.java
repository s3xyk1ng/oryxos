package com.oryxos.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Memory 模块配置属性。
 * 通过 {@code oryxos.memory.*} 绑定，支持 Markdown / SQLite / Mem0 三档后端切换。
 */
@ConfigurationProperties(prefix = "oryxos.memory")
public class MemoryProperties {

    /**
     * 后端类型：markdown（默认）、sqlite、mem0。
     */
    private String backend = "markdown";

    /**
     * ARCHIVAL 加载窗口大小，单位是完整记忆条数。
     */
    private int archivalLimit = 200;

    private final Markdown markdown = new Markdown();
    private final Mem0 mem0 = new Mem0();

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public int getArchivalLimit() {
        return archivalLimit;
    }

    public void setArchivalLimit(int archivalLimit) {
        this.archivalLimit = archivalLimit;
    }

    public Markdown getMarkdown() {
        return markdown;
    }

    public Mem0 getMem0() {
        return mem0;
    }

    /**
     * Markdown 后端配置。
     */
    public static class Markdown {

        /**
         * 默认存储路径。
         */
        private String path = ".oryxos/memory/MEMORY.md";

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    /**
     * Mem0 后端配置。
     */
    public static class Mem0 {

        /**
         * 自托管 Mem0 服务地址，建议通过环境变量注入。
         */
        private String baseUrl = "${MEM0_BASE_URL}";

        /**
         * Mem0 API 凭证，必须走环境变量注入，禁止落明文。
         */
        private String apiKey = "${MEM0_API_KEY}";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
