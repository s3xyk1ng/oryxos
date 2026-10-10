package com.oryxos.web.controller;

import com.oryxos.web.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * System API：健康检查 + 运行信息。
 */
@RestController
@RequestMapping("/api/v1")
public class SystemApiController {

    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("status", "UP"));
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "OryxOS");
        info.put("version", "1.0.0-SNAPSHOT");
        info.put("java", System.getProperty("java.version"));
        info.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        info.put("uptime", ManagementFactory.getRuntimeMXBean().getUptime() + "ms");
        info.put("memory", Map.of(
                "max", Runtime.getRuntime().maxMemory() / 1024 / 1024 + "MB",
                "total", Runtime.getRuntime().totalMemory() / 1024 / 1024 + "MB",
                "free", Runtime.getRuntime().freeMemory() / 1024 / 1024 + "MB"
        ));
        return ApiResponse.ok(info);
    }
}
