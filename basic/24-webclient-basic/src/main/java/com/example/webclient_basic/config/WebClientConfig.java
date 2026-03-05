package com.example.webclient_basic.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration class to create WebClient beans.
 */
@Configuration
public class WebClientConfig {

    /**
     * Creates a WebClient bean with the base URL configured via properties.
     * WebClient.Builder is automatically provided and configured by Spring Boot.
     */
    @Bean
    public WebClient jsonPlaceholderWebClient(WebClient.Builder builder,
            @Value("${app.api.base-url}") String baseUrl) {
        return builder
                .baseUrl(baseUrl)
                .build();
    }
}
