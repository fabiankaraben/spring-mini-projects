package com.example.corsconfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the CORS Global Configuration mini-project.
 * This Spring Boot application demonstrates how to configure Cross-Origin Resource Sharing (CORS)
 * globally for all API endpoints using a WebMvcConfigurer.
 */
@SpringBootApplication
public class CorsGlobalConfigApplication {

    /**
     * Main method to start the Spring Boot application.
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(CorsGlobalConfigApplication.class, args);
    }
}
