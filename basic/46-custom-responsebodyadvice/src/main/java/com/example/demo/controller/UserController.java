package com.example.demo.controller;

import com.example.demo.model.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for managing User resources.
 * <p>
 * This controller demonstrates endpoints that return different types of data (Object and String).
 * The responses from these endpoints will be intercepted by {@link com.example.demo.advice.GlobalResponseBodyAdvice}
 * and wrapped in a standard {@link com.example.demo.wrapper.ResponseWrapper}.
 * </p>
 */
@RestController
@RequestMapping("/users")
public class UserController {

    /**
     * Retrieves a user by their ID.
     *
     * @param id The ID of the user to retrieve.
     * @return A {@link User} object, which will be wrapped in the standard response format.
     */
    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id) {
        // Simulating a user retrieval
        return new User(id, "John Doe", "john.doe@example.com");
    }

    /**
     * Returns a simple greeting string.
     *
     * @return A String message. Since this is a String, it requires special handling in the ResponseBodyAdvice
     *         to ensure it is properly wrapped as JSON.
     */
    @GetMapping("/hello")
    public String hello() {
        return "Hello World";
    }
}
