package com.example.stompwebsockets.integration;

import com.example.stompwebsockets.service.TopicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the {@code /api/topics} REST controller.
 *
 * <h2>What is tested</h2>
 * <ul>
 *   <li>{@code POST /api/topics?name=news} creates a topic and returns 201.</li>
 *   <li>{@code POST /api/topics?name=news} is idempotent (a second call also returns 201).</li>
 *   <li>{@code GET /api/topics} returns all registered topics in alphabetical order.</li>
 *   <li>{@code GET /api/topics/{name}/history} returns the message history for a topic.</li>
 *   <li>{@code GET /api/topics/{name}/history} returns 404 for an unknown topic.</li>
 * </ul>
 *
 * <h2>Technology</h2>
 * <ul>
 *   <li>{@code @SpringBootTest} – loads the full application context so the
 *       full Spring MVC pipeline (interceptors, validation, exception handling)
 *       is exercised, not just the controller in isolation.</li>
 *   <li>{@code @AutoConfigureMockMvc} – configures {@link MockMvc} so HTTP
 *       requests can be issued without starting a real TCP port, making these
 *       tests faster than {@code RANDOM_PORT} tests.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Topic REST API – Integration Tests")
class TopicRestIntegrationTest {

    /** MockMvc client for issuing HTTP requests to the embedded Spring context. */
    @Autowired
    private MockMvc mockMvc;

    /** Injected to pre-populate history and reset state between tests. */
    @Autowired
    private TopicService topicService;

    /** Reset all topic state before each test. */
    @BeforeEach
    void setUp() {
        topicService.clearAll();
    }

    // ── POST /api/topics ──────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/topics?name=news should return 201 and confirm the topic name")
    void createTopic_shouldReturn201WithTopicName() throws Exception {
        mockMvc.perform(post("/api/topics").param("name", "news"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.topic", is("news")))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/topics?name=NEWS should normalise to lower-case")
    void createTopic_shouldNormaliseToLowerCase() throws Exception {
        mockMvc.perform(post("/api/topics").param("name", "NEWS"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.topic", is("news")));
    }

    @Test
    @DisplayName("POST /api/topics should be idempotent – second call also returns 201")
    void createTopic_shouldBeIdempotent() throws Exception {
        mockMvc.perform(post("/api/topics").param("name", "sports"))
                .andExpect(status().isCreated());

        // Second call for the same name must not fail
        mockMvc.perform(post("/api/topics").param("name", "sports"))
                .andExpect(status().isCreated());
    }

    // ── GET /api/topics ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/topics should return empty array when no topics exist")
    void listTopics_shouldReturnEmptyArrayWhenNoTopics() throws Exception {
        mockMvc.perform(get("/api/topics").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/topics should return all registered topic names in alphabetical order")
    void listTopics_shouldReturnAllTopicsAlphabetically() throws Exception {
        // Pre-register topics in reverse alphabetical order
        topicService.registerTopic("sports");
        topicService.registerTopic("news");
        topicService.registerTopic("technology");

        mockMvc.perform(get("/api/topics").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                // Service sorts alphabetically; verify order
                .andExpect(jsonPath("$[0]", is("news")))
                .andExpect(jsonPath("$[1]", is("sports")))
                .andExpect(jsonPath("$[2]", is("technology")));
    }

    // ── GET /api/topics/{name}/history ────────────────────────────────────────

    @Test
    @DisplayName("GET /api/topics/{name}/history should return 404 for an unknown topic")
    void getHistory_shouldReturn404ForUnknownTopic() throws Exception {
        mockMvc.perform(get("/api/topics/unknown/history").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/topics/{name}/history should return empty array for a topic with no messages")
    void getHistory_shouldReturnEmptyArrayForTopicWithNoMessages() throws Exception {
        topicService.registerTopic("news");

        mockMvc.perform(get("/api/topics/news/history").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/topics/{name}/history should return messages published to that topic")
    void getHistory_shouldReturnPublishedMessages() throws Exception {
        // Pre-populate history directly via the service (bypassing STOMP for REST-only test)
        topicService.buildAndPublish("news",
                new com.example.stompwebsockets.dto.PublishRequest("alice", "First news"));
        topicService.buildAndPublish("news",
                new com.example.stompwebsockets.dto.PublishRequest("bob", "Second news"));

        mockMvc.perform(get("/api/topics/news/history").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].sender", is("alice")))
                .andExpect(jsonPath("$[0].payload", is("First news")))
                .andExpect(jsonPath("$[1].sender", is("bob")))
                .andExpect(jsonPath("$[1].payload", is("Second news")));
    }

    @Test
    @DisplayName("GET /api/topics/{name}/history should not include messages from other topics")
    void getHistory_shouldIsolateMessagesByTopic() throws Exception {
        topicService.buildAndPublish("news",
                new com.example.stompwebsockets.dto.PublishRequest("alice", "News message"));
        topicService.buildAndPublish("sports",
                new com.example.stompwebsockets.dto.PublishRequest("bob", "Sports message"));

        // Only news messages should appear in /news/history
        mockMvc.perform(get("/api/topics/news/history").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].payload", is("News message")));
    }
}
