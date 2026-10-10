package com.oryxos.core.model;

/**
 * Sandbox 接口——工具执行前的安全校验。
 * 核心阶段为应用层白名单校验（"劝阻级"防线），不做容器级隔离。
 * 接口独立于任何实现，扩展阶段新增容器/microVM 实现时不修改此接口。
 */
public interface Sandbox {

    /**
     * 校验动作是否被允许。
     *
     * @param action 待执行的 Sandbox 动作
     * @throws SecurityException 如果动作不被允许
     */
    void enforce(SandboxAction action);
}
