package com.example.jsonrequestparser.controller;

import com.example.jsonrequestparser.model.UserRegistrationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sliced integration test that focuses solely on the web layer (Controller).
 * Uses @WebMvcTest which disables full auto-configuration and only applies
 * configuration relevant to MVC tests.
 */
@WebMvcTest(UserController.class)
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc; // Entry point for MVC testing

    @Autowired
    private ObjectMapper objectMapper; // Utility to convert objects to JSON

    @Test
    void registerUser_WithValidRequest_ShouldReturnCreatedAndResponse() throws Exception {
        // Arrange
        UserRegistrationRequest validRequest = new UserRegistrationRequest("springdev", "test@example.com",
                "securePassword123");
        String jsonPayload = objectMapper.writeValueAsString(validRequest);

        // Act & Assert
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.username").value("springdev"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void registerUser_WithInvalidEmail_ShouldReturnBadRequest() throws Exception {
        // Arrange - Invalid email provided
        UserRegistrationRequest invalidRequest = new UserRegistrationRequest("springdev", "invalid-email",
                "securePassword123");
        String jsonPayload = objectMapper.writeValueAsString(invalidRequest);

        // Act & Assert
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isBadRequest()); // Expecting HTTP 400 Bad Request due to validation error
    }

    @Test
    void registerUser_WithBlankUsername_ShouldReturnBadRequest() throws Exception {
        // Arrange - Blank username provided
        UserRegistrationRequest invalidRequest = new UserRegistrationRequest("", "test@example.com",
                "securePassword123");
        String jsonPayload = objectMapper.writeValueAsString(invalidRequest);

        // Act & Assert
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isBadRequest());
    }
}
