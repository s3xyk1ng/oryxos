package com.oryxos.core.model;

import java.time.Instant;

/**
 * 对话消息。
 */
public class Message {

    private String role;      // user / assistant / system / tool
    private String content;
    private Instant timestamp;

    public Message() {
        this.timestamp = Instant.now();
    }

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = Instant.now();
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
