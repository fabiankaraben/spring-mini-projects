package com.example.helloworld.controller;

import com.example.helloworld.service.HelloWorldService;
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
 * Sliced integration tests for the HelloWorldController.
 * Using @WebMvcTest, only the web layer is instantiated, not the full
 * application context.
 */
@WebMvcTest(HelloWorldController.class)
class HelloWorldControllerTest {

    // MockMvc allows us to perform HTTP requests without starting a server
    @Autowired
    private MockMvc mockMvc;

    // MockitoBean replaces the real HelloWorldService in the Spring App Context
    // with a mock
    @MockitoBean
    private HelloWorldService helloWorldService;

    @Test
    void testSayHelloReturnsMockedMessage() throws Exception {
        // Arrange
        String mockResponse = "Hello Mocked World";
        when(helloWorldService.getGreetingMessage()).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string(mockResponse));
    }
}
