package com.devscope.model.dto;

import lombok.Builder;
import lombok.Getter;


@Getter
@Builder
public class GeminiEmbeddingRequest extends LlmEmbeddingRequest {
    GeminiContent content;
    String model;

    @Builder.Default
    Integer outputDimensionality = 1536;
}
