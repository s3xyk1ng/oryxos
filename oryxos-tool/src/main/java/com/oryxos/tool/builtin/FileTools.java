package com.oryxos.tool.builtin;

import com.oryxos.core.model.OryxTool;
import com.oryxos.core.model.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 内置文件操作工具：read_file、write_file、list_dir。
 */
@Component
public class FileTools {

    private static final Logger log = LoggerFactory.getLogger(FileTools.class);

    @Component("readFileTool")
    public static class ReadFileTool implements OryxTool {
        @Override
        public String getName() { return "read_file"; }

        @Override
        public String getDescription() { return "Read the content of a file at the given path."; }

        @Override
        public Map<String, Object> getInputSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", Map.of(
                    "path", Map.of("type", "string", "description", "The file path to read")
            ));
            schema.put("required", List.of("path"));
            return schema;
        }

        @Override
        public ToolResult execute(Map<String, Object> input) {
            String path = (String) input.get("path");
            if (path == null || path.isBlank()) {
                return ToolResult.fail("path parameter is required", false);
            }
            try {
                Path p = Path.of(path);
                if (!Files.exists(p)) {
                    return ToolResult.fail("File not found: " + path, false);
                }
                if (!Files.isRegularFile(p)) {
                    return ToolResult.fail("Not a regular file: " + path, false);
                }
                String content = Files.readString(p);
                return ToolResult.ok(content);
            } catch (IOException e) {
                return ToolResult.fail("Failed to read file: " + e.getMessage(), true);
            }
        }
    }

    @Component("writeFileTool")
    public static class WriteFileTool implements OryxTool {
        @Override
        public String getName() { return "write_file"; }

        @Override
        public String getDescription() { return "Write content to a file at the given path. Creates parent directories if needed."; }

        @Override
        public Map<String, Object> getInputSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", Map.of(
                    "path", Map.of("type", "string", "description", "The file path to write to"),
                    "content", Map.of("type", "string", "description", "The content to write")
            ));
            schema.put("required", List.of("path", "content"));
            return schema;
        }

        @Override
        public ToolResult execute(Map<String, Object> input) {
            String path = (String) input.get("path");
            String content = (String) input.get("content");
            if (path == null || path.isBlank()) {
                return ToolResult.fail("path parameter is required", false);
            }
            if (content == null) content = "";
            try {
                Path p = Path.of(path);
                Files.createDirectories(p.getParent());
                Files.writeString(p, content);
                return ToolResult.ok("File written: " + path + " (" + content.length() + " chars)");
            } catch (IOException e) {
                return ToolResult.fail("Failed to write file: " + e.getMessage(), true);
            }
        }
    }

    @Component("listDirTool")
    public static class ListDirTool implements OryxTool {
        @Override
        public String getName() { return "list_dir"; }

        @Override
        public String getDescription() { return "List files and directories in the given path."; }

        @Override
        public Map<String, Object> getInputSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", Map.of(
                    "path", Map.of("type", "string", "description", "The directory path to list",
                            "default", ".")
            ));
            schema.put("required", List.of());
            return schema;
        }

        @Override
        public ToolResult execute(Map<String, Object> input) {
            String path = (String) input.getOrDefault("path", ".");
            try {
                Path p = Path.of(path);
                if (!Files.isDirectory(p)) {
                    return ToolResult.fail("Not a directory: " + path, false);
                }
                try (Stream<Path> stream = Files.list(p)) {
                    String listing = stream
                            .map(fp -> {
                                String type = Files.isDirectory(fp) ? "[DIR] " : "[FILE] ";
                                return type + fp.getFileName().toString();
                            })
                            .collect(Collectors.joining("\n"));
                    return ToolResult.ok(listing.isEmpty() ? "(empty directory)" : listing);
                }
            } catch (IOException e) {
                return ToolResult.fail("Failed to list directory: " + e.getMessage(), true);
            }
        }
    }
}
