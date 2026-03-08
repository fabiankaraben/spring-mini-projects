package com.example.exceptiontranslators;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Exception Translators Spring Boot application.
 * This application demonstrates how to translate custom exceptions into standard HTTP responses.
 */
@SpringBootApplication
public class ExceptionTranslatorsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExceptionTranslatorsApplication.class, args);
    }

}
