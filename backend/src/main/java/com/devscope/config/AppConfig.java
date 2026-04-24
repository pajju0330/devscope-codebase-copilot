package com.devscope.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    @Value("${devscope.llm.api-key}")
    private String llmApiKey;

    @Value("${devscope.llm.base-url}")
    private String llmBaseUrl;

    @Bean
    public RestClient llmRestClient() {
        return RestClient.builder()
                .baseUrl(llmBaseUrl)
                .defaultHeader("x-goog-api-key", llmApiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
