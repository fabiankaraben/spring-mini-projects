package com.example.helloworld.service;

import org.springframework.stereotype.Service;

/**
 * Service class that contains the business logic.
 * Annotated with @Service so Spring can detect and manage it as a bean.
 */
@Service
public class HelloWorldService {

    /**
     * Returns a simple greeting message.
     * 
     * @return the hello world message.
     */
    public String getGreetingMessage() {
        return "Hello World";
    }
}
