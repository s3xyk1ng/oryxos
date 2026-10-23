package com.oryxos.core.model;

/**
 * 条件注册接口。实现此接口的工具，在 isAvailable() 返回 false 时不注册到 ToolRegistry。
 * 用于工具依赖的外部条件不满足时（如通知渠道表为空），自动隐藏工具，LLM 看不到。
 */
public interface ConditionalTool {

    /**
     * 判断工具是否可用。
     *
     * @return true 表示可注册，false 表示不注册
     */
    boolean isAvailable();
}
