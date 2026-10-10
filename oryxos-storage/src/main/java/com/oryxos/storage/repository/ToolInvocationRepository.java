package com.oryxos.storage.repository;

import com.oryxos.storage.entity.ToolInvocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ToolInvocationRepository extends JpaRepository<ToolInvocationEntity, Long> {
    List<ToolInvocationEntity> findBySessionId(String sessionId);
}
