package com.example.corsconfig;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global CORS (Cross-Origin Resource Sharing) configuration for the application.
 * CORS is a security feature implemented by web browsers to prevent web pages from making requests
 * to a different domain than the one that served the web page. This configuration allows
 * cross-origin requests from specified origins to access API endpoints.
 *
 * This class implements WebMvcConfigurer to configure CORS mappings globally,
 * applying to all endpoints that match the specified patterns.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * Configures CORS mappings for the application.
     * This method defines which origins, methods, headers, and credentials are allowed
     * for cross-origin requests to API endpoints.
     *
     * @param registry the CorsRegistry to configure CORS mappings
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Allow CORS for all endpoints under /api/**
        // In a real application, you should specify allowed origins instead of using "*"
        // For this educational project, we allow from localhost for development purposes
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*") // Allow from localhost with any port
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Common HTTP methods
                .allowedHeaders("*") // Allow all headers
                .allowCredentials(true); // Allow credentials like cookies or authorization headers
    }
}
