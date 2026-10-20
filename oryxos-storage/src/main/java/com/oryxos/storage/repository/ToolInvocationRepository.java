package com.oryxos.storage.repository;

import com.oryxos.storage.entity.ToolInvocationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolInvocationRepository extends JpaRepository<ToolInvocationEntity, Integer> {
    List<ToolInvocationEntity> findBySessionId(String sessionId);

    List<ToolInvocationEntity> findByToolCallId(String toolCallId);
}
