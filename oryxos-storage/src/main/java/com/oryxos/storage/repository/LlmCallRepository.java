package com.oryxos.storage.repository;

import com.oryxos.storage.entity.LlmCallEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LlmCallRepository extends JpaRepository<LlmCallEntity, Long> {
    List<LlmCallEntity> findBySessionId(String sessionId);
}
