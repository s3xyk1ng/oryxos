package com.oryxos.web.controller;

import com.oryxos.core.agent.*;
import com.oryxos.core.model.Session;
import com.oryxos.web.GlobalExceptionHandler;
import com.oryxos.web.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Agent API：无状态调用 Agent。
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentApiController {

    private final AgentService agentService;
    private final SessionManager sessionManager;
    private final ProfileRegistry profileRegistry;

    public AgentApiController(AgentService agentService, SessionManager sessionManager,
                              ProfileRegistry profileRegistry) {
        this.agentService = agentService;
        this.sessionManager = sessionManager;
        this.profileRegistry = profileRegistry;
    }

    @PostMapping("/{name}/invoke")
    public ApiResponse<String> invoke(@PathVariable String name,
                                      @RequestBody Map<String, String> body) {
        if (!profileRegistry.exists(name)) {
            throw new GlobalExceptionHandler.ResourceNotFoundException("Agent not found: " + name);
        }
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
        String userId = body.getOrDefault("userId", "api-user");
        Session session = sessionManager.getOrCreateSession("api", userId, name);
        String reply = agentService.process(session, message);
        return ApiResponse.ok(reply);
    }
}
