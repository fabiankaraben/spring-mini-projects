package com.example.corsconfig;

import org.springframework.stereotype.Service;

/**
 * Implementation of the GreetingService interface.
 * This service provides greeting messages for the application.
 * In a real application, this could interact with databases, external APIs, etc.
 */
@Service
public class GreetingServiceImpl implements GreetingService {

    /**
     * Returns a standard greeting message.
     * This method demonstrates a simple service operation that can be unit tested.
     *
     * @return the greeting message
     */
    @Override
    public String getGreeting() {
        return "Hello from CORS enabled API service!";
    }
}
