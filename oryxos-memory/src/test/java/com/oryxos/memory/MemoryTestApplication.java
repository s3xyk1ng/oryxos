package com.oryxos.memory;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 测试专用最小 Spring Boot 应用入口。
 */
@SpringBootApplication(scanBasePackages = {"com.oryxos.memory", "com.oryxos.storage"})
@EntityScan(basePackages = "com.oryxos.storage.entity")
@EnableJpaRepositories(basePackages = "com.oryxos.storage.repository")
public class MemoryTestApplication {
}
