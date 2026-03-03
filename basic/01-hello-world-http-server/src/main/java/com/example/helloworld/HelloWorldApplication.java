package com.example.helloworld;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class to bootstrap the Spring Boot backend.
 */
@SpringBootApplication
public class HelloWorldApplication {

    /**
     * Entry point of the Spring Boot application.
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        SpringApplication.run(HelloWorldApplication.class, args);
    }
}
