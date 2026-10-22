package com.oryxos.storage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Minimal Spring Boot application for storage integration tests. */
@SpringBootApplication
@EntityScan(basePackages = "com.oryxos.storage.entity")
@EnableJpaRepositories(basePackages = "com.oryxos.storage.repository")
public class StorageTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(StorageTestApplication.class, args);
    }
}
