package com.example.sessionmanagement;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for SessionController using @WebMvcTest.
 * These tests verify the web layer functionality, including session handling.
 */
@WebMvcTest(SessionController.class)
class SessionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Test the full flow: setting a user name and then retrieving it.
     * Verifies that session data persists across requests.
     */
    @Test
    void testSetAndGetUserName() throws Exception {
        // Create a mock session
        MockHttpSession session = new MockHttpSession();

        // First, set the user name in the session
        mockMvc.perform(post("/api/session/user")
                .param("name", "Alice")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string("User name 'Alice' stored in session."));

        // Then, retrieve the user name from the same session
        mockMvc.perform(get("/api/session/user")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, Alice!"));
    }

    /**
     * Test getting user name when no user is set in the session.
     * Verifies that a 404 is returned.
     */
    @Test
    void testGetUserName_WhenNotSet() throws Exception {
        // Create a new session without setting user
        MockHttpSession session = new MockHttpSession();

        // Try to get user name
        mockMvc.perform(get("/api/session/user")
                .session(session))
                .andExpect(status().isNotFound());
    }
}
