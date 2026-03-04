package com.example.profileconfig.controller;

import com.example.profileconfig.service.MessageService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sliced integration test for the MessageController.
 * We use @WebMvcTest which only loads the web tier (controllers, filters, etc.)
 * but doesn't load the full context or the service layer implementations.
 * We then mock the service layer with @MockitoBean.
 */
@WebMvcTest(MessageController.class)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Using @MockitoBean from Spring Boot 3.4 to inject a mock of the
     * MessageService
     * into the Spring application context and replace any existing bean of the same
     * type.
     */
    @MockitoBean
    private MessageService messageService;

    @Test
    void shouldReturnDefaultEnvironmentAndMockedMessage() throws Exception {
        // Arrange: tell our mock what to return when getMessage() is called
        Mockito.when(messageService.getMessage()).thenReturn("Mocked test message");

        // Act & Assert: simulate a GET request to /api/status
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                // In slice tests, the application.properties is loaded which sets
                // spring.profiles.active=dev, so app.environment.name is "Development"
                .andExpect(jsonPath("$.environment").value("Development"))
                .andExpect(jsonPath("$.message").value("Mocked test message"));
    }
}
