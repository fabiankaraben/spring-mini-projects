package com.example.configurationserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Configuration Server.
 * <p>
 * These tests verify that the server correctly serves configuration properties
 * for different profiles (default, dev, prod) from the local classpath.
 * </p>
 * <p>
 * We use {@code @SpringBootTest} with a random port to start the full application context
 * and {@code TestRestTemplate} to make actual HTTP requests to the running server.
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigServerIntegrationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * Verifies that the default configuration is returned when no specific profile is requested
     * (or the default profile is implied).
     */
    @Test
    void shouldReturnDefaultConfiguration() {
        // Request configuration for "client-app" with "default" profile
        ResponseEntity<String> response = restTemplate.getForEntity("/client-app/default", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Verify content matches what is in client-app.properties
        assertThat(response.getBody()).contains("Welcome to the Default Environment");
        assertThat(response.getBody()).contains("user.role");
    }

    /**
     * Verifies that the development specific configuration is returned.
     * This should include properties from client-app-dev.properties and fallbacks from client-app.properties.
     */
    @Test
    void shouldReturnDevConfiguration() {
        ResponseEntity<String> response = restTemplate.getForEntity("/client-app/dev", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Verify content matches what is in client-app-dev.properties
        assertThat(response.getBody()).contains("Welcome to the Development Environment");
        assertThat(response.getBody()).contains("Dev-Admin");
    }
    
    /**
     * Verifies that the production specific configuration is returned.
     */
    @Test
    void shouldReturnProdConfiguration() {
        ResponseEntity<String> response = restTemplate.getForEntity("/client-app/prod", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Verify content matches what is in client-app-prod.properties
        assertThat(response.getBody()).contains("Welcome to the Production Environment");
    }
}
