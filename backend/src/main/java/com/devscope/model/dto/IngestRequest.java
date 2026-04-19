package com.devscope.model.dto;

import jakarta.validation.constraints.NotBlank;

public record IngestRequest(
        @NotBlank String repoName,
        String repoUrl  // null means zip upload
) {}
