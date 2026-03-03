package com.example.staticfileserver;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller providing a simple API endpoint to test the server's running
 * state.
 * This is primarily intended as an academic example of handling an HTTP API
 * alongside static resources.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final HealthService healthService;

    // Dependency injection via constructor
    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /**
     * Endpoint to check health status.
     * 
     * @return the status provided by HealthService
     */
    @GetMapping("/health")
    public String checkHealth() {
        return healthService.getStatus();
    }
}
