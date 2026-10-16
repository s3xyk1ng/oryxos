package com.oryxos.provider;

import com.oryxos.core.agent.ProviderRegistry;
import com.oryxos.core.model.ProviderDef;
import com.oryxos.core.model.ProviderNotFoundException;
import java.util.concurrent.ConcurrentHashMap;

/** ProviderRegistry 的内存 Map 实现。按 name 注册和查找，找不到抛异常。 */
public class InMemoryProviderRegistry implements ProviderRegistry {

    private final ConcurrentHashMap<String, ProviderDef> providers = new ConcurrentHashMap<>();

    @Override
    public void save(ProviderDef def) {
        providers.put(def.name(), def);
    }

    @Override
    public ProviderDef find(String name) {
        ProviderDef def = providers.get(name);
        if (def == null) {
            throw new ProviderNotFoundException(name);
        }
        return def;
    }

    /** 当前注册的 Provider 数量（测试用）。 */
    public int size() {
        return providers.size();
    }
}
