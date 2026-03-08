package com.example.devtools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for GreetingController using @WebMvcTest.
 * 
 * This creates a sliced context that only loads beans relevant to the web layer.
 * It uses MockMvc to simulate HTTP requests.
 */
@WebMvcTest(GreetingController.class)
class GreetingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GreetingService greetingService;

    @Test
    void greetingEndpointShouldReturnMessage() throws Exception {
        // Arrange
        String expectedMessage = "Hello, Integration Test!";
        when(greetingService.getGreeting()).thenReturn(expectedMessage);

        // Act & Assert
        mockMvc.perform(get("/greet"))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedMessage));
    }
}
