package com.devscope.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class GeminiGenerateContentRequest {
    @JsonProperty("system_instruction")
    GeminiContent systemInstruction;

    List<GeminiContent> contents;
    GeminiGenerationConfig generationConfig;
}
