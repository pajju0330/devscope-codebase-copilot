package com.devscope.model.repository;

import com.devscope.model.entity.DependencyEdgeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DependencyEdgeRepository extends JpaRepository<DependencyEdgeEntity, UUID> {

    List<DependencyEdgeEntity> findByRepoId(UUID repoId);

    // Find all methods called BY a given class/method (outgoing edges)
    List<DependencyEdgeEntity> findByRepoIdAndCallerClassAndCallerMethod(
            UUID repoId, String callerClass, String callerMethod);

    // Find all callers OF a given class/method (incoming edges)
    List<DependencyEdgeEntity> findByRepoIdAndCalleeClassAndCalleeMethod(
            UUID repoId, String calleeClass, String calleeMethod);

    @Query("""
        SELECT d FROM DependencyEdgeEntity d
        WHERE d.repoId = :repoId
          AND (d.callerClass IN :classNames OR d.calleeClass IN :classNames)
        """)
    List<DependencyEdgeEntity> findEdgesInvolvingClasses(UUID repoId, List<String> classNames);
}
