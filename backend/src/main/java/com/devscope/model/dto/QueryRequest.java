package com.devscope.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record QueryRequest(
        @NotNull UUID repoId,
        @NotBlank String question,
        QueryMode mode
) {
    public enum QueryMode {
        STANDARD,
        EXPLAIN_LIKE_NEW  // "explain this service like I'm new"
    }

    public QueryRequest {
        if (mode == null) mode = QueryMode.STANDARD;
    }
}
