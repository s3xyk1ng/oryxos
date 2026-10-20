package com.oryxos.core.agent;

/** Agent 配置缺失异常：AGENT.md 等核心文件不存在时 fail-fast 抛出， 不静默降级。 */
public class AgentConfigException extends RuntimeException {

    public AgentConfigException(String message) {
        super(message);
    }

    public AgentConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
