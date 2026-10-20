package com.oryxos.core.agent;

import com.oryxos.core.model.Profile;

/**
 * Profile 线程上下文：基于 ThreadLocal 实现请求级隔离。 虚拟线程下每个请求天然独立，Tool 执行时通过 get() 读取当前 Agent。 AgentService 在
 * finally 中必须 clear()，防止线程复用时 Profile 泄漏。
 */
public final class ProfileContext {

    private static final ThreadLocal<Profile> CURRENT = new ThreadLocal<>();

    private ProfileContext() {}

    /** 设置当前线程的 Profile。 */
    public static void set(Profile profile) {
        CURRENT.set(profile);
    }

    /** 获取当前线程的 Profile。 */
    public static Profile get() {
        return CURRENT.get();
    }

    /** 清除当前线程的 Profile，必须在 finally 块中调用。 */
    public static void clear() {
        CURRENT.remove();
    }
}
