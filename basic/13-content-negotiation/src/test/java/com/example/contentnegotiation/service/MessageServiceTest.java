package com.example.contentnegotiation.service;

import com.example.contentnegotiation.model.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @InjectMocks
    private MessageService messageService;

    @Test
    @DisplayName("Should return a non-null message with predefined content")
    void getMessage_ShouldReturnMessage() {
        // Act
        Message result = messageService.getMessage();

        // Assert
        assertNotNull(result, "The generated message should not be null");
        assertNotNull(result.id(), "The message ID should not be null");
        assertEquals("Hello, this is a response demonstrating Content Negotiation!", result.content(),
                "The message content should match the expected string");
    }
}
