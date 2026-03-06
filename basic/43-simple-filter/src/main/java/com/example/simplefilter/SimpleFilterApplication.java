package com.example.simplefilter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Simple Filter Spring Boot application.
 * This application demonstrates the use of a javax.servlet.Filter for low-level request manipulation.
 * The filter will log incoming requests and add a custom header to the response.
 */
@SpringBootApplication
public class SimpleFilterApplication {

    /**
     * Main method to start the Spring Boot application.
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(SimpleFilterApplication.class, args);
    }
}
