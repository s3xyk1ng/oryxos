package com.oryxos.web.controller;

import com.oryxos.core.agent.ToolRegistry;
import com.oryxos.web.dto.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** Tool API：列出所有可用工具。 */
@RestController
@RequestMapping("/api/v1/tools")
public class ToolApiController {

    private final ToolRegistry toolRegistry;

    public ToolApiController(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @GetMapping
    public ApiResponse<List<Map<String, String>>> listTools() {
        List<Map<String, String>> tools =
                toolRegistry.getAllTools().stream()
                        .map(
                                t ->
                                        Map.of(
                                                "name",
                                                t.getName(),
                                                "description",
                                                t.getDescription() != null
                                                        ? t.getDescription()
                                                        : ""))
                        .toList();
        return ApiResponse.ok(tools);
    }
}
