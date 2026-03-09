package com.example.stompwebsockets.integration;

import com.example.stompwebsockets.domain.TopicMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box Testcontainers integration tests.
 *
 * <h2>What makes this different from the other integration tests?</h2>
 * <p>The {@link TopicPubSubIntegrationTest} and {@link TopicRestIntegrationTest}
 * classes run the application in-process (same JVM), which means tests can
 * inject Spring beans and access in-memory state directly. That is convenient
 * but does not prove the packaged Docker image works correctly.
 *
 * <p>This test class pulls the pre-built application image ({@code stomp-over-websockets:latest})
 * and starts it in a real Docker container. All interactions are purely
 * external – HTTP REST calls and STOMP WebSocket connections – exactly as a
 * real client would use the service. This gives confidence that:
 * <ul>
 *   <li>The fat JAR produced by {@code ./mvnw package} starts correctly.</li>
 *   <li>The container's exposed port is mapped and reachable.</li>
 *   <li>The STOMP endpoint, REST endpoint, and topic routing all work
 *       end-to-end from outside the JVM.</li>
 * </ul>
 *
 * <h2>Prerequisites</h2>
 * <p>The Docker image must exist locally before these tests run. The
 * {@code Dockerfile} in the project root builds it with:
 * <pre>
 *   docker build -t stomp-over-websockets:latest .
 * </pre>
 * When the image is not present, Docker will fail to start the container and
 * Testcontainers will throw, causing these tests to fail with a clear error.
 *
 * <h2>Testcontainers annotations</h2>
 * <ul>
 *   <li>{@code @Testcontainers} – activates Testcontainers JUnit 5 extension
 *       which manages container lifecycle for fields annotated with
 *       {@code @Container}.</li>
 *   <li>{@code @Container} – the container is started once before all tests in
 *       this class and stopped after the last test (shared container pattern,
 *       which is faster than creating a new container per test).</li>
 * </ul>
 */
@Testcontainers
@DisplayName("Testcontainers – Black-box Integration Tests")
class ContainerIntegrationTest {

    /**
     * Docker container running the application image.
     *
     * <p>Testcontainers maps port 8080 inside the container to a random free
     * host port so there are no conflicts with other running services.
     * The {@code waitingFor} strategy polls the {@code /api/topics} endpoint
     * until it returns HTTP 200, ensuring the application is fully started
     * before any test runs.
     */
    @Container
    static GenericContainer<?> appContainer = new GenericContainer<>(
            // The image must be built with: docker build -t stomp-over-websockets:latest .
            DockerImageName.parse("stomp-over-websockets:latest"))
            .withExposedPorts(8080)
            // Wait until the REST endpoint is reachable – confirms the app started
            .waitingFor(Wait.forHttp("/api/topics").forStatusCode(200));

    /** Seconds to wait for async STOMP messages before timing out. */
    private static final int TIMEOUT_SECONDS = 10;

    /** Milliseconds to wait after subscribing before sending a message. */
    private static final long SUBSCRIBE_SETTLE_MS = 500;

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the base URL for HTTP REST requests to the containerised application.
     *
     * <p>Testcontainers maps container port 8080 to a random host port;
     * {@link GenericContainer#getMappedPort(int)} returns that host port.
     *
     * @return base URL, e.g., {@code http://localhost:55432}
     */
    private String baseUrl() {
        return "http://localhost:" + appContainer.getMappedPort(8080);
    }

    /**
     * Returns the WebSocket URL for STOMP connections to the container.
     *
     * @return STOMP endpoint URL, e.g., {@code ws://localhost:55432/ws/websocket}
     */
    private String wsUrl() {
        return "ws://localhost:" + appContainer.getMappedPort(8080) + "/ws/websocket";
    }

    /**
     * Creates and connects a STOMP client to the containerised application.
     *
     * @return an active {@link StompSession}
     * @throws Exception if the connection cannot be established within the timeout
     */
    private StompSession connectStompClient() throws Exception {
        WebSocketStompClient stompClient =
                new WebSocketStompClient(new StandardWebSocketClient());

        // Configure Jackson with JSR-310 support for Instant serialisation
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);

        return stompClient.connectAsync(wsUrl(), new StompSessionHandlerAdapter() {
            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                throw new RuntimeException("STOMP transport error: " + exception.getMessage(), exception);
            }
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Creates a {@link StompFrameHandler} that enqueues each received
     * {@link TopicMessage} into the given {@link BlockingQueue}.
     *
     * @param queue target queue
     * @return a frame handler for use with {@code session.subscribe()}
     */
    private StompFrameHandler topicMessageHandler(BlockingQueue<TopicMessage> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TopicMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.offer((TopicMessage) payload);
            }
        };
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Container: GET /api/topics should return HTTP 200 with an empty array on startup")
    void container_listTopics_shouldReturnOkWithEmptyArray() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/topics"))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        // On a fresh container the topic list is empty
        assertThat(response.body()).isEqualTo("[]");
    }

    @Test
    @DisplayName("Container: POST /api/topics?name=news should create the topic and return 201")
    void container_createTopic_shouldReturn201() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/topics?name=news"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("news");
    }

    @Test
    @DisplayName("Container: STOMP publish to /app/topic/sports should broadcast to /topic/sports subscribers")
    void container_stompPublish_shouldBroadcastToSubscribers() throws Exception {
        BlockingQueue<TopicMessage> received = new LinkedBlockingQueue<>();

        StompSession session = connectStompClient();

        // Subscribe to the sports topic
        session.subscribe("/topic/sports", topicMessageHandler(received));
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        // Build JSON payload and send via STOMP
        com.example.stompwebsockets.dto.PublishRequest publishRequest =
                new com.example.stompwebsockets.dto.PublishRequest("bob", "Goal scored!");
        session.send("/app/topic/sports", publishRequest);

        // Wait for the broadcast to arrive from the containerised application
        TopicMessage message = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(message).isNotNull();
        assertThat(message.getTopic()).isEqualTo("sports");
        assertThat(message.getSender()).isEqualTo("bob");
        assertThat(message.getPayload()).isEqualTo("Goal scored!");
        assertThat(message.getTimestamp()).isNotNull();

        session.disconnect();
    }

    @Test
    @DisplayName("Container: REST history endpoint should return messages published via STOMP")
    void container_historyEndpoint_shouldReflectStompPublishedMessages() throws Exception {
        BlockingQueue<TopicMessage> received = new LinkedBlockingQueue<>();
        StompSession session = connectStompClient();

        // Subscribe to confirm the message was processed before checking REST
        session.subscribe("/topic/finance", topicMessageHandler(received));
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        // Publish via STOMP
        session.send("/app/topic/finance",
                new com.example.stompwebsockets.dto.PublishRequest("alice", "Market update"));

        // Wait for delivery confirmation
        TopicMessage delivered = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(delivered).isNotNull();

        // Now verify the REST history endpoint shows the same message
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest historyReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/topics/finance/history"))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> historyResponse =
                client.send(historyReq, HttpResponse.BodyHandlers.ofString());

        assertThat(historyResponse.statusCode()).isEqualTo(200);
        assertThat(historyResponse.body()).contains("alice");
        assertThat(historyResponse.body()).contains("Market update");

        session.disconnect();
    }
}
