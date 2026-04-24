package com.devscope.model.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GeminiPart {
    String text;
}
