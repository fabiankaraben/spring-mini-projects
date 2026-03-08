package com.example.demo.controller;

import com.example.demo.advice.GlobalResponseBodyAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link UserController} with {@link GlobalResponseBodyAdvice}.
 * <p>
 * These tests use {@code @WebMvcTest} to slice the application context and load only the web layer.
 * We explicitly include {@code GlobalResponseBodyAdvice} in the context configuration to ensure
 * that the advice is applied to the controller responses.
 * The tests verify that the HTTP responses are correctly wrapped in the standard JSON structure.
 * </p>
 */
@WebMvcTest(UserController.class)
@ContextConfiguration(classes = {UserController.class, GlobalResponseBodyAdvice.class})
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getUserById_shouldReturnWrappedUser() throws Exception {
        mockMvc.perform(get("/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("John Doe"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void hello_shouldReturnWrappedString() throws Exception {
        mockMvc.perform(get("/users/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Success"))
                .andExpect(jsonPath("$.data").value("Hello World"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
