package com.oryxos.storage.repository;

import com.oryxos.storage.entity.NotifyChannelEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotifyChannelRepository extends JpaRepository<NotifyChannelEntity, Integer> {
    Optional<NotifyChannelEntity> findByName(String name);

    List<NotifyChannelEntity> findByType(String type);
}
