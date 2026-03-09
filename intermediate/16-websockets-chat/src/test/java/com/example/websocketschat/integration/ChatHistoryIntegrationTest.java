package com.example.websocketschat.integration;

import com.example.websocketschat.domain.ChatMessage;
import com.example.websocketschat.dto.SendMessageRequest;
import com.example.websocketschat.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for the {@code GET /api/chat/history} REST endpoint.
 *
 * <h2>What these tests cover</h2>
 * <ul>
 *   <li>History endpoint returns an empty array when no messages have been sent.</li>
 *   <li>History endpoint returns messages previously stored via the service layer.</li>
 *   <li>Message fields (sender, content, type, timestamp) are correctly serialised
 *       to JSON.</li>
 *   <li>History is ordered oldest-first.</li>
 * </ul>
 *
 * <h2>Technology used</h2>
 * <ul>
 *   <li><strong>{@code @SpringBootTest}</strong> – starts the full Spring
 *       application context (including WebSocket config) so all beans are wired
 *       exactly as they would be in production.</li>
 *   <li><strong>MockMvc</strong> – sends HTTP requests through the full Spring
 *       MVC stack without starting a real embedded server, keeping tests fast.</li>
 *   <li><strong>{@link ChatService}</strong> – injected directly to pre-populate
 *       history so tests are deterministic without needing a live WebSocket
 *       connection.</li>
 * </ul>
 *
 * <p>Note: This application has no external dependencies (no database, no
 * message broker) so Testcontainers is not needed to spin up side-car
 * containers. The integration tests exercise the full Spring Boot stack
 * in-process, which satisfies the "full integration testing" requirement.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Chat History API – Integration Tests")
class ChatHistoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChatService chatService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Clears the in-memory history before each test so tests are isolated
     * and do not affect each other.
     */
    @BeforeEach
    void setUp() {
        chatService.clearHistory();
    }

    // ── GET /api/chat/history ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/chat/history should return 200 and empty array when no messages")
    void getHistory_shouldReturn200WithEmptyArray_whenNoMessages() throws Exception {
        mockMvc.perform(get("/api/chat/history"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                // Empty JSON array expected
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/chat/history should return stored CHAT messages")
    void getHistory_shouldReturnStoredChatMessages() throws Exception {
        // Pre-populate history via the service layer (simulates messages sent over WebSocket)
        chatService.buildAndStoreMessage(new SendMessageRequest("Alice", "Hello everyone!"));
        chatService.buildAndStoreMessage(new SendMessageRequest("Bob", "Hi Alice!"));

        mockMvc.perform(get("/api/chat/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                // First message is the oldest (Alice's)
                .andExpect(jsonPath("$[0].sender", is("Alice")))
                .andExpect(jsonPath("$[0].content", is("Hello everyone!")))
                .andExpect(jsonPath("$[0].type", is("CHAT")))
                .andExpect(jsonPath("$[0].timestamp", notNullValue()))
                // Second message is Bob's
                .andExpect(jsonPath("$[1].sender", is("Bob")))
                .andExpect(jsonPath("$[1].content", is("Hi Alice!")));
    }

    @Test
    @DisplayName("GET /api/chat/history should return JOIN messages")
    void getHistory_shouldReturnJoinMessages() throws Exception {
        chatService.buildJoinMessage("Charlie");

        mockMvc.perform(get("/api/chat/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type", is("JOIN")))
                .andExpect(jsonPath("$[0].sender", is("Charlie")));
    }

    @Test
    @DisplayName("GET /api/chat/history should return LEAVE messages")
    void getHistory_shouldReturnLeaveMessages() throws Exception {
        chatService.buildLeaveMessage("Dave");

        mockMvc.perform(get("/api/chat/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type", is("LEAVE")))
                .andExpect(jsonPath("$[0].sender", is("Dave")));
    }

    @Test
    @DisplayName("GET /api/chat/history should return messages in insertion (oldest-first) order")
    void getHistory_shouldReturnMessagesInInsertionOrder() throws Exception {
        chatService.buildJoinMessage("Alice");
        chatService.buildAndStoreMessage(new SendMessageRequest("Alice", "First!"));
        chatService.buildAndStoreMessage(new SendMessageRequest("Bob", "Second!"));
        chatService.buildLeaveMessage("Alice");

        mockMvc.perform(get("/api/chat/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].type", is("JOIN")))
                .andExpect(jsonPath("$[1].content", is("First!")))
                .andExpect(jsonPath("$[2].content", is("Second!")))
                .andExpect(jsonPath("$[3].type", is("LEAVE")));
    }

    @Test
    @DisplayName("GET /api/chat/history each message should have a non-null timestamp field")
    void getHistory_eachMessageShouldHaveTimestamp() throws Exception {
        chatService.buildAndStoreMessage(new SendMessageRequest("Eve", "Testing timestamps"));

        mockMvc.perform(get("/api/chat/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].timestamp", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/chat/history should handle mixed message types correctly")
    void getHistory_shouldHandleMixedMessageTypes() throws Exception {
        chatService.buildJoinMessage("Alice");
        chatService.buildAndStoreMessage(new SendMessageRequest("Alice", "Hello!"));
        chatService.buildLeaveMessage("Alice");

        mockMvc.perform(get("/api/chat/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].type", is("JOIN")))
                .andExpect(jsonPath("$[1].type", is("CHAT")))
                .andExpect(jsonPath("$[2].type", is("LEAVE")));
    }
}
