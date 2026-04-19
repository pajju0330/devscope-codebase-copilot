package com.devscope.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    @Value("${openai.api-key}")
    private String openAiApiKey;

    @Value("${openai.base-url}")
    private String openAiBaseUrl;

    @Bean
    public RestClient openAiRestClient() {
        return RestClient.builder()
                .baseUrl(openAiBaseUrl)
                .defaultHeader("Authorization", "Bearer " + openAiApiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
