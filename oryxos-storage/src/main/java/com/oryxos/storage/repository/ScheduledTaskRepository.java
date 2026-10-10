package com.oryxos.storage.repository;

import com.oryxos.storage.entity.ScheduledTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ScheduledTaskRepository extends JpaRepository<ScheduledTaskEntity, Long> {
    Optional<ScheduledTaskEntity> findByTaskKey(String taskKey);
}
