package com.oryxos.cli.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.*;

/**
 * oryxos init：创建 .oryxos/ 目录及子目录，创建默认 Bootstrap 文件和 MEMORY.md。
 * 操作幂等。
 */
@Command(name = "init", description = "Initialize OryxOS workspace")
public class InitCommand implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(InitCommand.class);

    @Override
    public void run() {
        Path root = Path.of(".");
        Path oryxosDir = root.resolve(".oryxos");

        // Create directory structure
        String[] subdirs = {"agents", "skills", "memory", "sessions", "logs"};
        for (String dir : subdirs) {
            createDirIfAbsent(oryxosDir.resolve(dir));
        }

        // Create default Bootstrap files
        createFileIfAbsent(oryxosDir.resolve("AGENTS.md"),
                "# OryxOS Agent Behavior\n\nProject-level agent behavior description.\n");
        createFileIfAbsent(oryxosDir.resolve("SOUL.md"),
                "# Agent Soul\n\nDefault agent personality definition.\n");
        createFileIfAbsent(oryxosDir.resolve("USER.md"),
                "# User Preferences\n\nUser-specific preferences.\n");

        // Create MEMORY.md
        createFileIfAbsent(oryxosDir.resolve("memory").resolve("MEMORY.md"),
                "# Memory\n\n## Core Memory\n\n(Core memory: always retained, never truncated)\n\n## Archival Memory\n\n(Archival memory: can be truncated, keyword search applies here)\n");

        log.info("OryxOS workspace initialized at {}", oryxosDir.toAbsolutePath().normalize());
        System.out.println("✓ OryxOS workspace initialized");
    }

    private void createDirIfAbsent(Path dir) {
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
                log.debug("Created directory: {}", dir);
            } catch (IOException e) {
                log.error("Failed to create directory {}: {}", dir, e.getMessage());
            }
        }
    }

    private void createFileIfAbsent(Path file, String defaultContent) {
        if (!Files.exists(file)) {
            try {
                Files.writeString(file, defaultContent);
                log.debug("Created file: {}", file);
            } catch (IOException e) {
                log.error("Failed to create file {}: {}", file, e.getMessage());
            }
        }
    }
}
