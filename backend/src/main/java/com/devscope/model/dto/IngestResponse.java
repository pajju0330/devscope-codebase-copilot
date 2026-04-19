package com.devscope.model.dto;

import java.util.UUID;

public record IngestResponse(
        UUID repoId,
        String status,
        String message
) {}
