package com.devscope.model.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "repos")
public class RepoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String url;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(name = "ingested_at")
    private OffsetDateTime ingestedAt = OffsetDateTime.now();

    public RepoEntity() {}

    public RepoEntity(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    public String getStatus() { return status; }
    public String getError() { return error; }
    public OffsetDateTime getIngestedAt() { return ingestedAt; }

    public void setStatus(String status) { this.status = status; }
    public void setError(String error) { this.error = error; }
    public void setIngestedAt(OffsetDateTime ingestedAt) { this.ingestedAt = ingestedAt; }
}
