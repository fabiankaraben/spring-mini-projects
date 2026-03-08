package com.example.devtools;

import org.springframework.stereotype.Service;

/**
 * Service to provide greeting messages.
 * 
 * Separating logic into a service allows for better testing and separation of concerns.
 */
@Service
public class GreetingService {

    /**
     * Returns a greeting message.
     * 
     * @return The greeting string.
     */
    public String getGreeting() {
        return "Hello, DevTools!";
    }
}
