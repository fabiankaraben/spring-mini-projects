package com.example.helloworld.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the HelloWorldService.
 * This class validates the business logic without loading the entire Spring
 * Context.
 */
class HelloWorldServiceTest {

    private final HelloWorldService helloWorldService = new HelloWorldService();

    @Test
    void testGetGreetingMessageReturnsCorrectString() {
        // Arrange
        String expectedMessage = "Hello World";

        // Act
        String actualMessage = helloWorldService.getGreetingMessage();

        // Assert
        assertEquals(expectedMessage, actualMessage, "The greeting message should be exactly 'Hello World'");
    }
}
