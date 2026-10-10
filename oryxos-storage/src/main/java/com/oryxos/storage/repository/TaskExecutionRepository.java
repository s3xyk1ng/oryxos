package com.oryxos.storage.repository;

import com.oryxos.storage.entity.TaskExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskExecutionRepository extends JpaRepository<TaskExecutionEntity, Long> {
    List<TaskExecutionEntity> findByTaskKeyOrderByStartedAtDesc(String taskKey);
}
