package com.example.jsonresponseapi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for the UserService class using standard JUnit 5 features.
 * This class isolates and tests only the business logic within the service
 * layer.
 */
public class UserServiceTest {

    /**
     * Test to verify that getCurrentUser() provides a valid User object.
     * We'll confirm that the properties match the expected hardcoded values.
     */
    @Test
    public void testGetCurrentUser() {
        // Arrange
        UserService userService = new UserService();

        // Act
        User user = userService.getCurrentUser();

        // Assert
        assertNotNull(user, "User should not be null");
        assertEquals(1L, user.getId(), "User ID should be 1");
        assertEquals("Alice Smith", user.getName(), "User name should match");
        assertEquals("alice.smith@example.com", user.getEmail(), "User email should match");
        assertEquals("ADMIN", user.getRole(), "User role should match");
    }
}
