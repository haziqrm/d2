package com.example.coursework1.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IlpConfig {
    @Bean
    public String ilpEndpoint() {
        return System.getenv().getOrDefault(
                "ILP_ENDPOINT",
                "https://ilp-rest-2025-bvh6e9hschfagrgy.ukwest-01.azurewebsites.net/"
        );
    }
}