package com.oryxos.core.model;

/** LLM API 调用失败时抛出，包装原始异常。 */
public class ProviderCallException extends RuntimeException {

    public ProviderCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
