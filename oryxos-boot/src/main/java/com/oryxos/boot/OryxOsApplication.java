package com.oryxos.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;

@SpringBootApplication(scanBasePackages = "com.oryxos", exclude = OpenAiAutoConfiguration.class)
@EntityScan(basePackages = "com.oryxos.storage.entity")
@EnableJpaRepositories(basePackages = "com.oryxos.storage.repository")
public class OryxOsApplication {

    public static void main(String[] args) {
        SpringApplication.run(OryxOsApplication.class, args);
    }
}
