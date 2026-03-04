package com.example.profileconfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Profile Specific Config Demo.
 * 
 * This application demonstrates how to use the Spring @Profile annotation
 * to load different configuration properties and bean implementations
 * depending on the active environment (e.g., dev, prod).
 */
@SpringBootApplication
public class ProfileSpecificConfigApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProfileSpecificConfigApplication.class, args);
    }

}
