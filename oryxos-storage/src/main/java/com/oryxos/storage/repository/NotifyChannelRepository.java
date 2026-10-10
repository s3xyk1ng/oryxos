package com.oryxos.storage.repository;

import com.oryxos.storage.entity.NotifyChannelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotifyChannelRepository extends JpaRepository<NotifyChannelEntity, Long> {
    Optional<NotifyChannelEntity> findByName(String name);
    List<NotifyChannelEntity> findByType(String type);
}
