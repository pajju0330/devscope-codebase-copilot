package com.devscope.model.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "dependency_edges")
public class DependencyEdgeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "repo_id", nullable = false)
    private UUID repoId;

    @Column(name = "caller_file")
    private String callerFile;

    @Column(name = "caller_class")
    private String callerClass;

    @Column(name = "caller_method")
    private String callerMethod;

    @Column(name = "callee_class")
    private String calleeClass;

    @Column(name = "callee_method")
    private String calleeMethod;

    public DependencyEdgeEntity() {}

    public DependencyEdgeEntity(UUID repoId, String callerFile, String callerClass,
                                String callerMethod, String calleeClass, String calleeMethod) {
        this.repoId = repoId;
        this.callerFile = callerFile;
        this.callerClass = callerClass;
        this.callerMethod = callerMethod;
        this.calleeClass = calleeClass;
        this.calleeMethod = calleeMethod;
    }

    public UUID getId() { return id; }
    public UUID getRepoId() { return repoId; }
    public String getCallerFile() { return callerFile; }
    public String getCallerClass() { return callerClass; }
    public String getCallerMethod() { return callerMethod; }
    public String getCalleeClass() { return calleeClass; }
    public String getCalleeMethod() { return calleeMethod; }
}
