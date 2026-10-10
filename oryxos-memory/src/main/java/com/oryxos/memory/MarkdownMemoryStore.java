package com.oryxos.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Markdown 文件的长期记忆存储（默认实现）。
 * 操作 .oryxos/memory/MEMORY.md，按 "## 核心记忆" / "## 归档记忆" 分区。
 */
@Component
public class MarkdownMemoryStore implements LongTermMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(MarkdownMemoryStore.class);
    private static final String CORE_HEADER = "## 核心记忆";
    private static final String ARCHIVAL_HEADER = "## 归档记忆";
    private static final int MAX_ARCHIVAL_LINES = 200;

    private final Path memoryFile;

    public MarkdownMemoryStore(
            @Value("${oryxos.memory.markdown.path:.oryxos/memory/MEMORY.md}") String path) {
        this.memoryFile = Path.of(path);
    }

    @Override
    public void append(String content, Scope scope) {
        ensureFileExists();
        try {
            String existing = Files.readString(memoryFile);
            String header = (scope == Scope.CORE) ? CORE_HEADER : ARCHIVAL_HEADER;
            String updated = appendToSection(existing, header, content);
            Files.writeString(memoryFile, updated);
            log.debug("Appended to {} memory: {}", scope, truncate(content, 50));
        } catch (IOException e) {
            log.error("Failed to append memory: {}", e.getMessage());
        }
    }

    @Override
    public MemoryContent load() {
        if (!Files.exists(memoryFile)) {
            return new MemoryContent("", "");
        }
        try {
            String content = Files.readString(memoryFile);
            return parseSections(content);
        } catch (IOException e) {
            log.error("Failed to load memory: {}", e.getMessage());
            return new MemoryContent("", "");
        }
    }

    @Override
    public List<String> recallByKeyword(String query) {
        List<String> results = new ArrayList<>();
        if (!Files.exists(memoryFile) || query == null || query.isBlank()) {
            return results;
        }
        try {
            String content = Files.readString(memoryFile);
            String lowerQuery = query.toLowerCase();
            for (String line : content.lines().toList()) {
                if (line.toLowerCase().contains(lowerQuery)
                        && !line.startsWith("#")) {
                    results.add(line.trim());
                }
            }
        } catch (IOException e) {
            log.error("Failed to recall memory: {}", e.getMessage());
        }
        return results;
    }

    @Override
    public String capabilities() {
        return "Markdown file store: " + memoryFile.toAbsolutePath().normalize();
    }

    // --- Internal ---

    private void ensureFileExists() {
        try {
            Files.createDirectories(memoryFile.getParent());
            if (!Files.exists(memoryFile)) {
                Files.writeString(memoryFile, "# Memory\n\n" + CORE_HEADER + "\n\n" + ARCHIVAL_HEADER + "\n");
            }
        } catch (IOException e) {
            log.error("Failed to create memory file: {}", e.getMessage());
        }
    }

    private MemoryContent parseSections(String content) {
        String core = extractSection(content, CORE_HEADER);
        String archival = extractSection(content, ARCHIVAL_HEADER);
        // Truncate archival if needed
        if (archival != null) {
            String[] lines = archival.split("\n");
            if (lines.length > MAX_ARCHIVAL_LINES) {
                archival = String.join("\n",
                        java.util.Arrays.copyOfRange(lines, lines.length - MAX_ARCHIVAL_LINES, lines.length));
            }
        }
        return new MemoryContent(core != null ? core : "", archival != null ? archival : "");
    }

    private String extractSection(String content, String header) {
        int start = content.indexOf(header);
        if (start < 0) return null;
        start = start + header.length();
        // Skip to end of line
        int nl = content.indexOf('\n', start);
        if (nl >= 0) start = nl + 1;
        // Find next section header
        int end = content.indexOf("\n## ", start);
        if (end < 0) end = content.length();
        return content.substring(start, end).trim();
    }

    private String appendToSection(String content, String header, String newLine) {
        int headerPos = content.indexOf(header);
        if (headerPos < 0) {
            // Section not found, append at end
            return content + "\n" + header + "\n- " + newLine + "\n";
        }
        // Find insertion point: after header line, before next ## or EOF
        int insertAfter = headerPos + header.length();
        int nl = content.indexOf('\n', insertAfter);
        if (nl >= 0) insertAfter = nl + 1;

        // Find next section
        int nextSection = content.indexOf("\n## ", insertAfter);
        if (nextSection < 0) {
            // Append at end
            return content + "- " + newLine + "\n";
        } else {
            // Insert before next section
            return content.substring(0, nextSection)
                    + "- " + newLine + "\n"
                    + content.substring(nextSection);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
