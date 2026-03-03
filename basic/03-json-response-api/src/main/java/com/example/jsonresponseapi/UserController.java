package com.example.jsonresponseapi;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The UserController handles HTTP requests relating to the /api/users endpoint.
 * The @RestController annotation combines @Controller and @ResponseBody, which
 * means that any return value from a mapping method will be written directly
 * to the HTTP response body, serialized into JSON by default using Jackson.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    /**
     * Dependency on the UserService to provide business logic and user data.
     */
    private final UserService userService;

    /**
     * Constructor-based dependency injection. Spring Boot automatically injects the
     * UserService singleton instance into this controller.
     *
     * @param userService The service responsible for user logic.
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Handles HTTP GET requests mapped to the /api/users/current URI.
     * When accessed, this endpoint returns a User POJO that Spring
     * Boot automatically transforms into a JSON HTTP response.
     *
     * @return A User object which gets serialized to JSON format.
     */
    @GetMapping("/current")
    public User getCurrentUser() {
        // Delegate retrieving data to the service layer.
        // We just return the Java object, and Spring Web takes care of serialization.
        return userService.getCurrentUser();
    }
}
