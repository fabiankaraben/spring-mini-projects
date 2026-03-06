package com.example.i18nmessages;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for MessageController using @WebMvcTest.
 * This test focuses on the web layer, verifying that the controller
 * correctly handles HTTP requests and returns localized messages
 * based on the Accept-Language header.
 */
@WebMvcTest(MessageController.class)
class MessageControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Test the /message/{key} endpoint with English locale.
     * Verifies that when Accept-Language is "en", the English message is returned.
     */
    @Test
    void testGetMessageEnglish() throws Exception {
        mockMvc.perform(get("/message/greeting")
                        .header("Accept-Language", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello World"));
    }

    /**
     * Test the /message/{key} endpoint with Spanish locale.
     * Verifies that when Accept-Language is "es", the Spanish message is returned.
     */
    @Test
    void testGetMessageSpanish() throws Exception {
        mockMvc.perform(get("/message/greeting")
                        .header("Accept-Language", "es"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hola Mundo"));
    }

    /**
     * Test the /message/{key} endpoint with French locale.
     * Verifies that when Accept-Language is "fr", the French message is returned.
     */
    @Test
    void testGetMessageFrench() throws Exception {
        mockMvc.perform(get("/message/greeting")
                        .header("Accept-Language", "fr"))
                .andExpect(status().isOk())
                .andExpect(content().string("Bonjour le Monde"));
    }

    /**
     * Test the /message/{key} endpoint with a different message key.
     * Verifies that different keys return the appropriate localized messages.
     */
    @Test
    void testGetMessageDifferentKeyEnglish() throws Exception {
        mockMvc.perform(get("/message/error.notfound")
                        .header("Accept-Language", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string("Message not found"));
    }
}
