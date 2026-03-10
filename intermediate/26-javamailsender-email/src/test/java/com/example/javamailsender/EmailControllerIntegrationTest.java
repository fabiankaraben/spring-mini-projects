package com.example.javamailsender;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full integration tests for the JavaMailSender Email application.
 *
 * <p>These tests verify the end-to-end flow from HTTP request → controller →
 * service → JavaMailSender → SMTP server (Mailpit).
 *
 * <h2>Test infrastructure</h2>
 * <ul>
 *   <li>{@link SpringBootTest} starts the full Spring application context,
 *       including the auto-configured {@link org.springframework.mail.javamail.JavaMailSender}.</li>
 *   <li>{@link AutoConfigureMockMvc} injects {@link MockMvc} to send HTTP
 *       requests through the full filter/controller/handler chain.</li>
 *   <li><strong>Mailpit</strong> (Docker image {@code axllent/mailpit}) is
 *       started via Testcontainers. Mailpit is a lightweight SMTP sink that
 *       accepts all email messages and exposes a REST API and web UI to
 *       inspect received messages. It is perfect for integration testing
 *       because it never actually delivers emails to the internet.</li>
 *   <li>{@link DynamicPropertySource} overrides {@code spring.mail.host} and
 *       {@code spring.mail.port} so the application's JavaMailSender connects
 *       to the Testcontainer's SMTP port instead of the default localhost:1025.</li>
 * </ul>
 *
 * <h2>Mailpit ports</h2>
 * <ul>
 *   <li><strong>1025</strong> – SMTP port (the application sends emails here).</li>
 *   <li><strong>8025</strong> – HTTP API port (tests query this to assert received messages).</li>
 * </ul>
 *
 * <h2>What is verified</h2>
 * <ul>
 *   <li>POST /api/email/plain returns 200 and the email arrives in Mailpit.</li>
 *   <li>POST /api/email/html returns 200 and the email arrives in Mailpit.</li>
 *   <li>POST /api/email/plain with invalid body returns 400.</li>
 *   <li>POST /api/email/html with missing required fields returns 400.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("EmailController integration tests (Mailpit + Testcontainers)")
class EmailControllerIntegrationTest {

    // ── Mailpit SMTP port (standard SMTP alternative for local dev) ────────────────
    private static final int SMTP_PORT = 1025;

    // ── Mailpit HTTP API port (used to query received messages) ───────────────────
    private static final int HTTP_PORT = 8025;

    /**
     * Mailpit container – a lightweight SMTP test server with a REST API.
     *
     * <p>{@code static} ensures a single container instance is shared across
     * all tests in this class, which avoids the overhead of stopping and
     * restarting Docker containers between tests.
     *
     * <p>Mailpit image: {@code axllent/mailpit} – the official Docker image.
     * It starts a combined SMTP + HTTP server on ports 1025 and 8025 respectively.
     */
    @Container
    static GenericContainer<?> mailpit = new GenericContainer<>(
            DockerImageName.parse("axllent/mailpit:latest"))
            // Expose SMTP port (application sends email here)
            .withExposedPorts(SMTP_PORT, HTTP_PORT);

    /**
     * Override Spring Mail configuration to point at the Mailpit container.
     *
     * <p>{@link DynamicPropertySource} is evaluated after the container starts
     * so the mapped ports are known. The overrides are injected into the Spring
     * {@link org.springframework.core.env.Environment} before the application
     * context is created.
     *
     * <p>{@code spring.mail.host} and {@code spring.mail.port} are the two
     * properties consumed by Spring Boot's {@code MailSenderAutoConfiguration}.
     */
    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        // Override SMTP host to the container's host (usually localhost via Docker)
        registry.add("spring.mail.host", mailpit::getHost);
        // Override SMTP port to the dynamically mapped host port for container port 1025
        registry.add("spring.mail.port", () -> mailpit.getMappedPort(SMTP_PORT));
    }

    /** MockMvc – performs HTTP requests against the mock servlet environment. */
    @Autowired
    MockMvc mockMvc;

    /**
     * Delete all messages from Mailpit before each test so that message count
     * assertions are not affected by emails sent in previous tests.
     *
     * <p>Mailpit's REST API exposes {@code DELETE /api/v1/messages} to wipe all stored messages.
     */
    @BeforeEach
    void clearMailpit() throws IOException, InterruptedException {
        // Build the Mailpit API base URL using the dynamically mapped HTTP port
        String mailpitApiUrl = "http://" + mailpit.getHost() + ":"
                + mailpit.getMappedPort(HTTP_PORT);

        // DELETE /api/v1/messages clears the Mailpit inbox
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mailpitApiUrl + "/api/v1/messages"))
                .DELETE()
                .build();
        client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // ── POST /api/email/plain ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/email/plain – returns 200 and email is received by Mailpit")
    void sendPlainText_returns200AndEmailArrivesInMailpit() throws Exception {
        // Given: a valid plain text email request body
        String requestBody = """
                {
                  "to": "recipient@example.com",
                  "subject": "Integration Test Plain Email",
                  "body": "Hello from the integration test!"
                }
                """;

        // When: POST /api/email/plain is called
        mockMvc.perform(post("/api/email/plain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // Then: HTTP 200 OK with status="sent"
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sent"))
                .andExpect(jsonPath("$.to").value("recipient@example.com"))
                .andExpect(jsonPath("$.message").value("Email sent successfully."));

        // Then: verify the email arrived in Mailpit via its REST API
        assertEmailCountInMailpit(1);
    }

    @Test
    @DisplayName("POST /api/email/html – returns 200 and HTML email is received by Mailpit")
    void sendHtmlEmail_returns200AndEmailArrivesInMailpit() throws Exception {
        // Given: a valid HTML email request body
        String requestBody = """
                {
                  "to": "html-recipient@example.com",
                  "subject": "Integration Test HTML Email",
                  "recipientName": "Integration Tester",
                  "message": "This is an HTML email sent during integration testing."
                }
                """;

        // When: POST /api/email/html is called
        mockMvc.perform(post("/api/email/html")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // Then: HTTP 200 OK with status="sent"
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sent"))
                .andExpect(jsonPath("$.to").value("html-recipient@example.com"))
                .andExpect(jsonPath("$.message").value("HTML email sent successfully."));

        // Then: verify the email arrived in Mailpit via its REST API
        assertEmailCountInMailpit(1);
    }

    @Test
    @DisplayName("POST /api/email/plain – returns 400 when 'to' field is missing")
    void sendPlainText_returns400WhenToIsMissing() throws Exception {
        // Given: request body missing the required "to" field
        String requestBody = """
                {
                  "subject": "No recipient",
                  "body": "This email has no recipient."
                }
                """;

        // When / Then: 400 Bad Request — Bean Validation rejects the missing field
        mockMvc.perform(post("/api/email/plain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("failed"));
    }

    @Test
    @DisplayName("POST /api/email/plain – returns 400 when 'to' has invalid email format")
    void sendPlainText_returns400WhenEmailFormatIsInvalid() throws Exception {
        // Given: an invalid email address in the "to" field
        String requestBody = """
                {
                  "to": "not-a-valid-email",
                  "subject": "Invalid recipient",
                  "body": "Body text."
                }
                """;

        // When / Then: 400 Bad Request — @Email constraint fires
        mockMvc.perform(post("/api/email/plain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("failed"));
    }

    @Test
    @DisplayName("POST /api/email/html – returns 400 when 'recipientName' is blank")
    void sendHtmlEmail_returns400WhenRecipientNameIsBlank() throws Exception {
        // Given: request body with a blank "recipientName"
        String requestBody = """
                {
                  "to": "user@example.com",
                  "subject": "Subject",
                  "recipientName": "",
                  "message": "Some message."
                }
                """;

        // When / Then: 400 Bad Request — @NotBlank constraint fires on recipientName
        mockMvc.perform(post("/api/email/html")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("failed"));
    }

    @Test
    @DisplayName("POST /api/email/plain – returns 400 when request body is empty")
    void sendPlainText_returns400WhenBodyIsEmpty() throws Exception {
        // When / Then: 400 on an empty JSON object (all fields are missing)
        mockMvc.perform(post("/api/email/plain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("failed"));
    }

    // ── Helper methods ────────────────────────────────────────────────────────────

    /**
     * Queries the Mailpit HTTP API and asserts that the expected number of
     * messages have been received.
     *
     * <p>Mailpit's {@code GET /api/v1/messages} endpoint returns a JSON object
     * with a {@code total} field indicating how many messages are in the inbox.
     * We use Java's built-in {@link HttpClient} (Java 11+) to make the request
     * and a simple string contains check for speed and simplicity.
     *
     * @param expectedCount the number of emails expected in Mailpit's inbox.
     * @throws IOException          if the HTTP request fails.
     * @throws InterruptedException if the thread is interrupted.
     */
    private void assertEmailCountInMailpit(int expectedCount)
            throws IOException, InterruptedException {

        String mailpitApiUrl = "http://" + mailpit.getHost() + ":"
                + mailpit.getMappedPort(HTTP_PORT);

        // GET /api/v1/messages returns: { "messages": [...], "total": N, ... }
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mailpitApiUrl + "/api/v1/messages"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode())
                .as("Mailpit API should return 200")
                .isEqualTo(200);

        // The Mailpit API returns JSON like: {"messages": [...], "messages_count": N, "total": N}
        // We do a simple string search for the expected total value to avoid pulling in
        // an extra JSON parsing dependency in tests.
        assertThat(response.body())
                .as("Mailpit inbox should contain %d message(s)", expectedCount)
                .contains("\"total\":" + expectedCount);
    }
}
