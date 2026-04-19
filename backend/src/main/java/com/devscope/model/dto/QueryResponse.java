package com.devscope.model.dto;

import java.util.List;

public record QueryResponse(
        List<String> relevantFiles,
        String explanation,
        String callGraphSummary,
        List<ChunkResult> chunks
) {
    public record ChunkResult(
            String filePath,
            String className,
            String methodName,
            int startLine,
            int endLine,
            String content,
            double similarity
    ) {}
}
