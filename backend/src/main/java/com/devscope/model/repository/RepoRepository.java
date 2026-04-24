package com.devscope.model.repository;

import com.devscope.model.entity.RepoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface RepoRepository extends JpaRepository<RepoEntity, UUID> {

    Optional<RepoEntity> findFirstByUrl(String url);

    @Modifying
    @Transactional
    @Query("UPDATE RepoEntity r SET r.status = :status WHERE r.id = :id")
    void updateStatus(UUID id, String status);

    @Modifying
    @Transactional
    @Query("UPDATE RepoEntity r SET r.status = :status, r.error = :error WHERE r.id = :id")
    void updateStatusWithError(UUID id, String status, String error);
}
