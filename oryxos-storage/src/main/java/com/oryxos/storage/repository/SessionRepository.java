package com.oryxos.storage.repository;

import com.oryxos.storage.entity.SessionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<SessionEntity, String> {
    List<SessionEntity> findByProfileName(String profileName);

    List<SessionEntity> findByStatus(String status);

    List<SessionEntity> findByChannelAndUserId(String channel, String userId);
}
