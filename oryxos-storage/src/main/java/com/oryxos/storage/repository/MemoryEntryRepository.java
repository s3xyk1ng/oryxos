package com.oryxos.storage.repository;

import com.oryxos.storage.entity.MemoryEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * memory_entries 数据访问。
 */
@Repository
public interface MemoryEntryRepository extends JpaRepository<MemoryEntryEntity, Long> {

    /**
     * 按 scope 和稳定 id 正序查询。
     */
    List<MemoryEntryEntity> findByScopeOrderByIdAsc(String scope);

    /**
     * 按 scope 和稳定 id 倒序取前 N 条。
     */
    @Query(value = "SELECT * FROM memory_entries WHERE scope = :scope ORDER BY id DESC LIMIT :limit",
            nativeQuery = true)
    List<MemoryEntryEntity> findLatestByScope(@Param("scope") String scope, @Param("limit") int limit);

    /**
     * 在指定 scope 中按普通子串包含匹配查询（不区分大小写）。
     * 使用 SQLite instr() 函数，避免 LIKE 通配符语义。
     */
    @Query(value = "SELECT * FROM memory_entries WHERE scope = :scope AND instr(lower(content), lower(:keyword)) > 0 ORDER BY id DESC",
            nativeQuery = true)
    List<MemoryEntryEntity> findByScopeAndContentContains(@Param("scope") String scope, @Param("keyword") String keyword);
}
