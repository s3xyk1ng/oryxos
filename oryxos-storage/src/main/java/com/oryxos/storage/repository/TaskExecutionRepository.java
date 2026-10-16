package com.oryxos.storage.repository;

import com.oryxos.storage.entity.TaskExecutionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskExecutionRepository extends JpaRepository<TaskExecutionEntity, Integer> {
    List<TaskExecutionEntity> findByTaskKeyOrderByStartedAtDesc(String taskKey);
}
