package com.devscope.model.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GeminiGenerationConfig {
    Double temperature;
    Integer maxOutputTokens;
}
