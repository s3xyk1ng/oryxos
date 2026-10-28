package com.oryxos.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 基于 Markdown 文件的长期记忆存储（默认实现）。
 * 操作 {@code .oryxos/memory/MEMORY.md}，按 "## 核心记忆" / "## 归档记忆" 分区。
 * 每条记忆以日期 header 开头， followed by 内容行，条目之间以空行分隔。
 */
public class MarkdownMemoryStore implements LongTermMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(MarkdownMemoryStore.class);

    private static final String CORE_HEADER = "## 核心记忆";
    private static final String ARCHIVAL_HEADER = "## 归档记忆";
    private static final String ENTRY_PREFIX = "### ";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    private final Path memoryFile;
    private final int archivalLimit;

    public MarkdownMemoryStore(
            @Value("${oryxos.memory.markdown.path:.oryxos/memory/MEMORY.md}") String path,
            @Value("${oryxos.memory.archival-limit:200}") int archivalLimit) {
        this.memoryFile = Path.of(path);
        this.archivalLimit = archivalLimit;
    }

    @Override
    public void append(String content, MemoryScope scope) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Memory content must not be blank");
        }
        if (scope == null) {
            throw new IllegalArgumentException("Memory scope must not be null");
        }
        long start = System.nanoTime();
        ensureFileExists();
        try {
            String existing = Files.readString(memoryFile);
            String header = (scope == MemoryScope.CORE) ? CORE_HEADER : ARCHIVAL_HEADER;
            String entry = ENTRY_PREFIX + DATE_FORMATTER.format(Instant.now()) + "\n" + content + "\n";
            String updated = appendToSection(existing, header, entry);
            Files.writeString(memoryFile, updated);
            log.info("Memory append completed: backend=markdown, scope={}, resultCount=1, durationMs={}",
                    scope, elapsedMillis(start));
        } catch (IOException e) {
            log.error("Memory backend failure: backend=markdown, operation=append, errorType={}",
                    e.getClass().getSimpleName());
            throw new MemoryStoreException("Failed to append memory to " + memoryFile, e);
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
            log.error("Memory backend failure: backend=markdown, operation=load, errorType={}",
                    e.getClass().getSimpleName());
            throw new MemoryStoreException("Failed to load memory from " + memoryFile, e);
        }
    }

    @Override
    public List<String> recallByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("Keyword must not be blank");
        }
        long start = System.nanoTime();
        List<String> results = new ArrayList<>();
        if (!Files.exists(memoryFile)) {
            log.info("Memory recall completed: backend=markdown, scope=ARCHIVAL, resultCount=0, durationMs={}",
                    elapsedMillis(start));
            return results;
        }
        try {
            String content = Files.readString(memoryFile);
            List<String> archivalEntries = extractEntries(content, ARCHIVAL_HEADER);
            String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
            for (String entry : archivalEntries) {
                if (entry.toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
                    results.add(entry);
                }
            }
        } catch (IOException e) {
            log.error("Memory backend failure: backend=markdown, operation=recall, errorType={}",
                    e.getClass().getSimpleName());
            throw new MemoryStoreException("Failed to recall memory from " + memoryFile, e);
        }
        log.info("Memory recall completed: backend=markdown, scope=ARCHIVAL, resultCount={}, durationMs={}",
                results.size(), elapsedMillis(start));
        return results;
    }

    // --- Internal ---

    private void ensureFileExists() {
        try {
            Files.createDirectories(memoryFile.getParent());
            if (!Files.exists(memoryFile)) {
                Files.writeString(memoryFile, "# Memory\n\n" + CORE_HEADER + "\n\n" + ARCHIVAL_HEADER + "\n");
            }
        } catch (IOException e) {
            log.error("Memory backend failure: backend=markdown, operation=create, errorType={}",
                    e.getClass().getSimpleName());
            throw new MemoryStoreException("Failed to create memory file " + memoryFile, e);
        }
    }

    private MemoryContent parseSections(String content) {
        List<String> coreEntries = extractEntries(content, CORE_HEADER);
        List<String> archivalEntries = extractEntries(content, ARCHIVAL_HEADER);

        // CORE 永不截断
        String coreSection = String.join("\n\n", coreEntries);

        // ARCHIVAL 只取最新 N 条（文件按追加顺序写入，最新在文件底部）
        List<String> latestArchival = archivalEntries.size() > archivalLimit
                ? archivalEntries.subList(archivalEntries.size() - archivalLimit, archivalEntries.size())
                : archivalEntries;
        String archivalSection = String.join("\n\n", latestArchival);

        return new MemoryContent(coreSection, archivalSection);
    }

    private List<String> extractEntries(String content, String header) {
        List<String> entries = new ArrayList<>();
        int start = content.indexOf(header);
        if (start < 0) {
            return entries;
        }
        start = start + header.length();
        int nl = content.indexOf('\n', start);
        if (nl >= 0) {
            start = nl + 1;
        }
        int end = content.indexOf("\n## ", start);
        if (end < 0) {
            end = content.length();
        }
        String section = content.substring(start, end).trim();
        if (section.isEmpty()) {
            return entries;
        }

        // 按 "### yyyy-MM-dd" 拆分条目；返回内容时去掉日期 header
        String[] lines = section.split("\n");
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (line.startsWith(ENTRY_PREFIX)) {
                if (current.length() > 0) {
                    entries.add(current.toString().trim());
                    current.setLength(0);
                }
                // 日期 header 仅作为分隔符，不加入条目内容
            } else if (current.length() > 0) {
                current.append("\n").append(line);
            } else if (!line.isBlank()) {
                current.append(line);
            }
        }
        if (current.length() > 0) {
            entries.add(current.toString().trim());
        }
        return entries;
    }

    private String appendToSection(String content, String header, String entry) {
        int headerPos = content.indexOf(header);
        if (headerPos < 0) {
            return content + "\n" + header + "\n\n" + entry;
        }
        int insertAfter = headerPos + header.length();
        int nl = content.indexOf('\n', insertAfter);
        if (nl >= 0) {
            insertAfter = nl + 1;
        }
        int nextSection = content.indexOf("\n## ", insertAfter);
        if (nextSection < 0) {
            // 在文件末尾追加，保持空行分隔
            String suffix = content.endsWith("\n") ? "" : "\n";
            return content + suffix + entry;
        } else {
            return content.substring(0, nextSection)
                    + entry
                    + content.substring(nextSection);
        }
    }

    private long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
