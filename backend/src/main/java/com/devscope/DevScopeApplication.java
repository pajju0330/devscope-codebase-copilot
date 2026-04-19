package com.devscope;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DevScopeApplication {
    public static void main(String[] args) {
        SpringApplication.run(DevScopeApplication.class, args);
    }
}
