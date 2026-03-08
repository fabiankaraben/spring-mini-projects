package com.example.hateoas.service;

import com.example.hateoas.domain.Greeting;
import org.springframework.stereotype.Service;

/**
 * Service layer for Greeting business logic.
 * Used to demonstrate Mockito in tests.
 */
@Service
public class GreetingService {

    private static final String TEMPLATE = "Hello, %s!";

    /**
     * Generates a greeting for the given name.
     *
     * @param name the name to greet
     * @return a new Greeting instance
     */
    public Greeting greet(String name) {
        return new Greeting(String.format(TEMPLATE, name));
    }
}
