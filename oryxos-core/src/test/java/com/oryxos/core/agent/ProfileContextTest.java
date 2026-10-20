package com.oryxos.core.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.oryxos.core.model.Profile;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ProfileContext ThreadLocal 行为验证。 */
class ProfileContextTest {

    @AfterEach
    void cleanup() {
        ProfileContext.clear();
    }

    @Test
    @DisplayName("set + get：设置后可获取同一 Profile")
    void setAndGet_returnSameProfile() {
        Profile profile = new Profile("test-agent");
        ProfileContext.set(profile);
        assertSame(profile, ProfileContext.get());
    }

    @Test
    @DisplayName("clear 后 get 为 null")
    void clearAfterSet_getReturnsNull() {
        Profile profile = new Profile("test-agent");
        ProfileContext.set(profile);
        ProfileContext.clear();
        assertNull(ProfileContext.get());
    }

    @Test
    @DisplayName("覆盖 set：连续设置两次，get 返回最后一次的值")
    void overrideSet_getReturnsLastValue() {
        Profile first = new Profile("agent-a");
        Profile second = new Profile("agent-b");
        ProfileContext.set(first);
        ProfileContext.set(second);
        assertSame(second, ProfileContext.get());
    }

    @Test
    @DisplayName("线程隔离：不同线程各自 get 到自己的 Profile")
    void threadIsolation_eachThreadGetsOwnProfile() throws Exception {
        Profile mainProfile = new Profile("main-agent");
        ProfileContext.set(mainProfile);

        Profile threadProfile = new Profile("thread-agent");
        AtomicReference<Profile> captured = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread thread =
                new Thread(
                        () -> {
                            ProfileContext.set(threadProfile);
                            captured.set(ProfileContext.get());
                            ProfileContext.clear();
                            latch.countDown();
                        });
        thread.start();
        latch.await();

        // 主线程的 Profile 不受子线程影响
        assertSame(mainProfile, ProfileContext.get());
        // 子线程获取到自己的 Profile
        assertSame(threadProfile, captured.get());
    }
}
