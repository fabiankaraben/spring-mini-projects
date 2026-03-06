package com.example.corsconfig;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for GreetingServiceImpl.
 * This test demonstrates unit testing of a service implementation using JUnit 5.
 * Since the service has no external dependencies, we instantiate it directly and test its methods.
 */
public class GreetingServiceImplTest {

    /**
     * Test the getGreeting method.
     * Verifies that the service returns the expected greeting message.
     */
    @Test
    public void testGetGreeting() {
        // Given: Create an instance of the service
        GreetingServiceImpl service = new GreetingServiceImpl();

        // When: Call the method under test
        String greeting = service.getGreeting();

        // Then: Assert the expected result
        assertEquals("Hello from CORS enabled API service!", greeting,
                "The greeting message should match the expected string");
    }
}
