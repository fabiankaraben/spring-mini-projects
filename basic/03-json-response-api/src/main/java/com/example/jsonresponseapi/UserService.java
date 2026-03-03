package com.example.jsonresponseapi;

import org.springframework.stereotype.Service;

/**
 * Service class for User-related business logic.
 * The @Service annotation marks this class as a Spring component, and
 * allows Spring to automatically discover and inject it where needed.
 */
@Service
public class UserService {

    /**
     * Retrieves the current user data.
     * In a real application, this would fetch data from a database or call another
     * API.
     * For this educational mini-project, we return a hardcoded POJO.
     * 
     * @return the User object holding the user data.
     */
    public User getCurrentUser() {
        // We create a new POJO instance
        return new User(1L, "Alice Smith", "alice.smith@example.com", "ADMIN");
    }
}
