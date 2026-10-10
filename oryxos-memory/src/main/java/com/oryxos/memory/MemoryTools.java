package com.oryxos.memory;

import com.oryxos.core.model.OryxTool;
import com.oryxos.core.model.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆工具：save_memory 和 recall_memory，注册到 ToolRegistry 供 Agent 调用。
 */
@Component
public class MemoryTools {

    private final MemoryService memoryService;

    public MemoryTools(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Component("saveMemoryTool")
    public class SaveMemoryTool implements OryxTool {
        @Override
        public String getName() { return "save_memory"; }

        @Override
        public String getDescription() {
            return "Save a piece of information to long-term memory. "
                    + "Use scope 'CORE' for critical facts that should never be forgotten, "
                    + "or 'ARCHIVAL' (default) for general reference information.";
        }

        @Override
        public Map<String, Object> getInputSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("content", Map.of("type", "string",
                    "description", "The information to save"));
            props.put("scope", Map.of("type", "string",
                    "description", "Memory scope: CORE or ARCHIVAL (default: ARCHIVAL)",
                    "enum", List.of("CORE", "ARCHIVAL")));
            schema.put("properties", props);
            schema.put("required", List.of("content"));
            return schema;
        }

        @Override
        public ToolResult execute(Map<String, Object> input) {
            String content = (String) input.get("content");
            if (content == null || content.isBlank()) {
                return ToolResult.fail("content parameter is required", false);
            }
            String scopeStr = (String) input.getOrDefault("scope", "ARCHIVAL");
            LongTermMemoryStore.Scope scope;
            try {
                scope = LongTermMemoryStore.Scope.valueOf(scopeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                scope = LongTermMemoryStore.Scope.ARCHIVAL;
            }
            memoryService.saveMemory(content, scope);
            return ToolResult.ok("Memory saved (" + scope + "): " + content);
        }
    }

    @Component("recallMemoryTool")
    public class RecallMemoryTool implements OryxTool {
        @Override
        public String getName() { return "recall_memory"; }

        @Override
        public String getDescription() {
            return "Search long-term memory by keyword. Returns matching memory entries.";
        }

        @Override
        public Map<String, Object> getInputSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("query", Map.of("type", "string",
                    "description", "Keyword to search for in memory"));
            schema.put("properties", props);
            schema.put("required", List.of("query"));
            return schema;
        }

        @Override
        public ToolResult execute(Map<String, Object> input) {
            String query = (String) input.get("query");
            if (query == null || query.isBlank()) {
                return ToolResult.fail("query parameter is required", false);
            }
            List<String> results = memoryService.recallMemory(query);
            if (results.isEmpty()) {
                return ToolResult.ok("No memories found for: " + query);
            }
            return ToolResult.ok("Found " + results.size() + " memories:\n"
                    + String.join("\n", results));
        }
    }
}
