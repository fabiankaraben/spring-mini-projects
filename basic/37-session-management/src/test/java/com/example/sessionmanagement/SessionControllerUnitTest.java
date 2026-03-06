package com.example.sessionmanagement;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SessionController using Mockito to mock dependencies.
 * These tests verify the controller's logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class SessionControllerUnitTest {

    @Mock
    private HttpSession session;

    @InjectMocks
    private SessionController controller;

    /**
     * Test setting a user name in the session.
     * Verifies that the session.setAttribute is called with correct parameters
     * and returns the expected response.
     */
    @Test
    void testSetUserName() {
        // Given
        String userName = "John Doe";

        // When
        ResponseEntity<String> response = controller.setUserName(userName, session);

        // Then
        verify(session).setAttribute("user", userName);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("User name 'John Doe' stored in session.", response.getBody());
    }

    /**
     * Test getting a user name when it exists in the session.
     * Verifies that the session.getAttribute is called and returns the correct response.
     */
    @Test
    void testGetUserName_WhenExists() {
        // Given
        String userName = "Jane Doe";
        when(session.getAttribute("user")).thenReturn(userName);

        // When
        ResponseEntity<String> response = controller.getUserName(session);

        // Then
        verify(session).getAttribute("user");
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("Hello, Jane Doe!", response.getBody());
    }

    /**
     * Test getting a user name when it does not exist in the session.
     * Verifies that a 404 response is returned when no user is found.
     */
    @Test
    void testGetUserName_WhenNotExists() {
        // Given
        when(session.getAttribute("user")).thenReturn(null);

        // When
        ResponseEntity<String> response = controller.getUserName(session);

        // Then
        verify(session).getAttribute("user");
        assertEquals(404, response.getStatusCodeValue());
        assertNull(response.getBody());
    }
}
