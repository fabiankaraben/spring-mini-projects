package com.example.sessionmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Session Management Spring Boot application.
 * This application demonstrates how to store and retrieve user-specific data
 * across HTTP requests using HttpSession.
 */
@SpringBootApplication
public class SessionManagementApplication {

    /**
     * Main method to start the Spring Boot application.
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(SessionManagementApplication.class, args);
    }
}
