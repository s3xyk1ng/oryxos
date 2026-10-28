package com.oryxos.storage.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 通知渠道实体：全局注册表，存储通知渠道配置。
 */
@Entity
@Table(name = "notify_channels")
public class NotifyChannelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INTEGER")
    private Long id;

    @Column(name = "name", length = 64, nullable = false, unique = true)
    private String name;

    @Column(name = "type", length = 32, nullable = false)
    private String type;  // webhook / feishu / dingtalk / wecom

    @Column(name = "url", length = 512, nullable = false)
    private String url;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "description", length = 256)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // --- Getters & Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
