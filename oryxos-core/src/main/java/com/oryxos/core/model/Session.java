package com.oryxos.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 会话：用户和 Agent 一次对话的上下文容器。 按 Channel + 用户 + Agent 联合标识。 */
public class Session {

    private String sessionId;
    private String profileName;
    private String channel;
    private String userId;
    private List<Message> messages = new ArrayList<>();
    private String status = "active";
    private Instant createdAt;
    private Instant lastActiveAt;
    private Instant archivedAt;

    public Session() {
        this.createdAt = Instant.now();
        this.lastActiveAt = Instant.now();
    }

    public Session(String sessionId, String profileName, String channel, String userId) {
        this();
        this.sessionId = sessionId;
        this.profileName = profileName;
        this.channel = channel;
        this.userId = userId;
    }

    public void addMessage(Message message) {
        messages.add(message);
        this.lastActiveAt = Instant.now();
    }

    public void archive() {
        this.status = "archived";
        this.archivedAt = Instant.now();
    }

    /** 截断对话历史，保留最近 N 轮（一轮 = 一问一答 = 2 条消息）。 */
    public void truncateHistory(int maxTurns) {
        int maxMessages = maxTurns * 2;
        if (messages.size() > maxMessages) {
            messages =
                    new ArrayList<>(
                            messages.subList(messages.size() - maxMessages, messages.size()));
        }
    }

    // --- Getters & Setters ---

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(Instant lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }
}
