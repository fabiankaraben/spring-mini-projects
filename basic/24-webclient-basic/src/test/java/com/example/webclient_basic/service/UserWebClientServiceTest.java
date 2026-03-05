package com.example.webclient_basic.service;

import com.example.webclient_basic.dto.User;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for UserWebClientService using okhttp3 MockWebServer.
 */
class UserWebClientServiceTest {

    private MockWebServer mockWebServer;
    private UserWebClientService userWebClientService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Configure the WebClient to use the mock server's dynamically assigned URL
        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        userWebClientService = new UserWebClientService(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testGetAllUsers() {
        // Arrange: prepare a mock JSON response representing the external API output
        String mockJsonResponse = """
                [
                    { "id": 1, "name": "Alice", "username": "alice", "email": "alice@example.com" },
                    { "id": 2, "name": "Bob", "username": "bob", "email": "bob@example.com" }
                ]
                """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(mockJsonResponse)
                .addHeader("Content-Type", "application/json"));

        // Act: call the service method
        List<User> users = userWebClientService.getAllUsers();

        // Assert: verify the returned value corresponds to the mock response
        assertNotNull(users);
        assertEquals(2, users.size());
        assertEquals("Alice", users.get(0).name());
    }

    @Test
    void testGetUserById() {
        // Arrange: prepare a mock JSON response for a single user
        String mockJsonResponse = """
                { "id": 10, "name": "Charlie", "username": "charlie", "email": "charlie@example.com" }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(mockJsonResponse)
                .addHeader("Content-Type", "application/json"));

        // Act: call the service method
        User user = userWebClientService.getUserById(10L);

        // Assert: verify the correctly mapped fields
        assertNotNull(user);
        assertEquals(10L, user.id());
        assertEquals("Charlie", user.name());
    }
}
