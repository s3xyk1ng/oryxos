package com.oryxos.memory;

/**
 * 长期记忆后端故障异常。
 * 用于区分后端不可用与存储为空两种情况，禁止把故障伪装成空记忆。
 */
public class MemoryStoreException extends RuntimeException {

    public MemoryStoreException(String message) {
        super(message);
    }

    public MemoryStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
