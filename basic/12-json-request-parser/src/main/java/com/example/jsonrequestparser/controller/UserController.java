package com.example.jsonrequestparser.controller;

import com.example.jsonrequestparser.model.UserRegistrationRequest;
import com.example.jsonrequestparser.model.UserRegistrationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that handles user registration requests.
 * Uses @RestController to indicate that the return types will automatically
 * be serialized into JSON in the response body.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    /**
     * Endpoint to register a new user.
     * The @RequestBody annotation maps the HttpRequest body to a Data Transfer
     * Object (DTO).
     * The @Valid annotation triggers Spring's validation mechanism before the
     * method body executes.
     *
     * @param request the parsed and validated user registration data
     * @return a ResponseEntity containing the response object and the HTTP status
     *         code
     */
    @PostMapping("/register")
    public ResponseEntity<UserRegistrationResponse> registerUser(@Valid @RequestBody UserRegistrationRequest request) {

        // In a real application, you would pass the DTO to a service layer to process
        // the registration and save it to a database here.

        // Simulating the user registration process
        UserRegistrationResponse response = new UserRegistrationResponse(
                "User registered successfully",
                request.getUsername(),
                "ACTIVE");

        // Returning an HTTP 201 Created status
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
