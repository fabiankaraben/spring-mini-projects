package com.example.contentnegotiation.controller;

import com.example.contentnegotiation.model.Message;
import com.example.contentnegotiation.service.MessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

/**
 * Sliced Integration test for MessageController using @WebMvcTest.
 * This test uses MockMvc to send HTTP requests to the controller and
 * parses the response without starting a full HTTP server.
 */
@WebMvcTest(MessageController.class)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessageService messageService;

    @Test
    @DisplayName("Should return JSON response when Accept header is Application/JSON")
    void shouldReturnJsonResponse() throws Exception {
        // Arrange
        Message mockedMessage = new Message("1234", "Hello, Content Negotiation JSON");
        when(messageService.getMessage()).thenReturn(mockedMessage);

        // Act & Assert
        // Perform a GET request setting the Accept header to JSON
        mockMvc.perform(get("/api/messages")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                // Verify the response contains JSON structure
                .andExpect(jsonPath("$.id").value("1234"))
                .andExpect(jsonPath("$.content").value("Hello, Content Negotiation JSON"));
    }

    @Test
    @DisplayName("Should return XML response when Accept header is Application/XML")
    void shouldReturnXmlResponse() throws Exception {
        // Arrange
        Message mockedMessage = new Message("5678", "Hello, Content Negotiation XML");
        when(messageService.getMessage()).thenReturn(mockedMessage);

        // Act & Assert
        // Perform a GET request setting the Accept header to XML
        mockMvc.perform(get("/api/messages")
                .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                // Verify the response contains XML structure. We use xpath to parse XML
                // structure
                .andExpect(xpath("/message/id").string("5678"))
                .andExpect(xpath("/message/content").string(containsString("Hello, Content Negotiation XML")));
    }
}
