package com.oryxos.core.context;

import com.oryxos.core.model.Profile;

/**
 * 当前 Agent Profile 的 ThreadLocal 持有者。
 * AgentService 在处理请求前将 Profile 放入，finally 中清除。
 * 工具执行时通过此类获取当前 Profile（如读取默认通知渠道）。
 */
public final class ProfileContext {

    private static final ThreadLocal<Profile> CURRENT = new ThreadLocal<>();

    private ProfileContext() {}

    public static void set(Profile profile) {
        CURRENT.set(profile);
    }

    public static Profile get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
