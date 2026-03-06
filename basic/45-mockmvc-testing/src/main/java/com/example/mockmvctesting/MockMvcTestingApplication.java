package com.example.mockmvctesting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the MockMvc Testing mini-project.
 * This Spring Boot application demonstrates how to use @WebMvcTest
 * for sliced integration testing without loading the full application context.
 */
@SpringBootApplication
public class MockMvcTestingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockMvcTestingApplication.class, args);
    }
}
