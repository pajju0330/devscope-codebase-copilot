package com.devscope.model.repository;

import com.devscope.model.entity.CodeChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CodeChunkRepository extends JpaRepository<CodeChunkEntity, UUID> {

    List<CodeChunkEntity> findByRepoId(UUID repoId);

    @Query("SELECT c.id FROM CodeChunkEntity c WHERE c.repoId = :repoId")
    List<UUID> findIdsByRepoId(UUID repoId);

    long countByRepoId(UUID repoId);
}
