package com.oryxos.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/** 启动时从 application.yaml 的 oryxos.providers[] 加载 Provider 定义到 Registry。 */
public class ProviderBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProviderBootstrap.class);

    private final InMemoryProviderRegistry registry;
    private final ProvidersProperties properties;

    public ProviderBootstrap(InMemoryProviderRegistry registry, ProvidersProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (var def : properties.toProviderDefs()) {
            registry.save(def);
            log.info("Loaded provider definition: name={}, type={}", def.name(), def.type());
        }
        log.info("Provider bootstrap complete, {} providers registered", registry.size());
    }
}
