package com.oryxos.core.agent;

import com.oryxos.core.model.Profile;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Profile 注册表：按 name 索引 Profile，提供 register/remove/exists/getByName 方法。 */
@Component
public class ProfileRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProfileRegistry.class);
    private final Map<String, Profile> profiles = new ConcurrentHashMap<>();

    public void register(Profile profile) {
        profiles.put(profile.getName(), profile);
        log.info("Registered profile: {}", profile.getName());
    }

    public void remove(String name) {
        profiles.remove(name);
        log.info("Removed profile: {}", name);
    }

    public boolean exists(String name) {
        return profiles.containsKey(name);
    }

    public Profile getByName(String name) {
        return profiles.get(name);
    }

    public Collection<Profile> getAll() {
        return profiles.values();
    }

    public int size() {
        return profiles.size();
    }
}
