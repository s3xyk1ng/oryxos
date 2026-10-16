package com.oryxos.storage.repository;

import com.oryxos.storage.entity.ScheduledTaskEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduledTaskRepository extends JpaRepository<ScheduledTaskEntity, Integer> {
    Optional<ScheduledTaskEntity> findByTaskKey(String taskKey);
}
