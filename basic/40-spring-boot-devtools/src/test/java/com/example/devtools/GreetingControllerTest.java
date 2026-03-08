package com.example.devtools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GreetingController using JUnit 5 and Mockito.
 * 
 * This test uses Mockito to mock the GreetingService, ensuring that we are
 * testing the controller in isolation without spinning up the Spring context.
 */
@ExtendWith(MockitoExtension.class)
class GreetingControllerTest {

    @Mock
    private GreetingService greetingService;

    @InjectMocks
    private GreetingController greetingController;

    @Test
    void greetingShouldReturnMessageFromService() {
        // Arrange
        String expectedMessage = "Hello, Mockito!";
        when(greetingService.getGreeting()).thenReturn(expectedMessage);

        // Act
        String actualMessage = greetingController.greeting();

        // Assert
        assertEquals(expectedMessage, actualMessage);
    }
}
