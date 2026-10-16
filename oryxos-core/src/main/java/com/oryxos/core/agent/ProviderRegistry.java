package com.oryxos.core.agent;

import com.oryxos.core.model.ProviderDef;

/** Provider 注册表。按 name 注册和查找 ProviderDef， 找不到时抛出 ProviderNotFoundException 而非静默降级。 */
public interface ProviderRegistry {

    /** 注册一个 Provider 定义。 */
    void save(ProviderDef def);

    /**
     * 按 name 查找 Provider 定义。
     *
     * @throws com.oryxos.core.model.ProviderNotFoundException 如果找不到
     */
    ProviderDef find(String name);
}
