package com.devscope.model.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "code_chunks")
public class CodeChunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "repo_id", nullable = false)
    private UUID repoId;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "class_name")
    private String className;

    @Column(name = "method_name")
    private String methodName;

    private String language;

    @Column(name = "start_line")
    private Integer startLine;

    @Column(name = "end_line")
    private Integer endLine;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // embedding column is managed via JdbcTemplate — not mapped here to avoid ORM type issues
    @Transient
    private float[] embedding;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public CodeChunkEntity() {}

    public CodeChunkEntity(UUID repoId, String filePath, String className, String methodName,
                           String language, Integer startLine, Integer endLine, String content) {
        this.repoId = repoId;
        this.filePath = filePath;
        this.className = className;
        this.methodName = methodName;
        this.language = language;
        this.startLine = startLine;
        this.endLine = endLine;
        this.content = content;
    }

    public UUID getId() { return id; }
    public UUID getRepoId() { return repoId; }
    public String getFilePath() { return filePath; }
    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public String getLanguage() { return language; }
    public Integer getStartLine() { return startLine; }
    public Integer getEndLine() { return endLine; }
    public String getContent() { return content; }
    public float[] getEmbedding() { return embedding; }

    public void setEmbedding(float[] embedding) { this.embedding = embedding; }
}
