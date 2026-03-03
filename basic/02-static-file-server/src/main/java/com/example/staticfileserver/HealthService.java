package com.example.staticfileserver;

import org.springframework.stereotype.Service;

/**
 * Service to provide application health status.
 * This is a simple educational example to demonstrate unit testing with
 * Mockito.
 */
@Service
public class HealthService {

    /**
     * Gets the current health status of the application.
     * 
     * @return a message indicating the health status
     */
    public String getStatus() {
        return "Application is running and serving static files!";
    }
}
