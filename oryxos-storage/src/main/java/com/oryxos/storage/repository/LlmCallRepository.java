package com.oryxos.storage.repository;

import com.oryxos.storage.entity.LlmCallEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmCallRepository extends JpaRepository<LlmCallEntity, Integer> {
    List<LlmCallEntity> findBySessionId(String sessionId);
}
