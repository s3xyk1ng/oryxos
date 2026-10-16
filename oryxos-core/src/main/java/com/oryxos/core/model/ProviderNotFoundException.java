package com.oryxos.core.model;

/** Provider 注册表中找不到指定 name 时抛出。不静默降级。 */
public class ProviderNotFoundException extends RuntimeException {

    public ProviderNotFoundException(String providerName) {
        super("Provider not found: " + providerName);
    }
}
