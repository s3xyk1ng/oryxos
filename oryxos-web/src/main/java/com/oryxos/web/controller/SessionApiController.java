package com.oryxos.web.controller;

import com.oryxos.core.agent.SessionManager;
import com.oryxos.core.model.Session;
import com.oryxos.web.GlobalExceptionHandler;
import com.oryxos.web.dto.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** Session API：创建会话、发送消息、查询历史、归档。 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionApiController {

    private final SessionManager sessionManager;
    private final com.oryxos.core.agent.AgentService agentService;

    public SessionApiController(
            SessionManager sessionManager, com.oryxos.core.agent.AgentService agentService) {
        this.sessionManager = sessionManager;
        this.agentService = agentService;
    }

    @PostMapping
    public ApiResponse<Session> createSession(@RequestBody Map<String, String> body) {
        String channel = body.getOrDefault("channel", "api");
        String userId = body.getOrDefault("userId", "api-user");
        String profileName = body.get("profileName");
        if (profileName == null || profileName.isBlank()) {
            throw new IllegalArgumentException("profileName is required");
        }
        Session session = sessionManager.getOrCreateSession(channel, userId, profileName);
        return ApiResponse.ok(session);
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<String> sendMessage(
            @PathVariable String id, @RequestBody Map<String, String> body) {
        Session session = sessionManager.getSession(id);
        if (session == null) {
            throw new GlobalExceptionHandler.ResourceNotFoundException("Session not found: " + id);
        }
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
        String reply = agentService.process(session, message);
        return ApiResponse.ok(reply);
    }

    @GetMapping("/{id}")
    public ApiResponse<Session> getSession(@PathVariable String id) {
        Session session = sessionManager.getSession(id);
        if (session == null) {
            throw new GlobalExceptionHandler.ResourceNotFoundException("Session not found: " + id);
        }
        return ApiResponse.ok(session);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> archiveSession(@PathVariable String id) {
        Session session = sessionManager.getSession(id);
        if (session == null) {
            throw new GlobalExceptionHandler.ResourceNotFoundException("Session not found: " + id);
        }
        sessionManager.archiveSession(id);
        return ApiResponse.ok();
    }
}
