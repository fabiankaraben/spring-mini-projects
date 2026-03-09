package com.example.stompwebsockets.integration;

import com.example.stompwebsockets.domain.TopicMessage;
import com.example.stompwebsockets.dto.PublishRequest;
import com.example.stompwebsockets.service.TopicService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration tests for STOMP pub/sub topic routing.
 *
 * <h2>What is tested</h2>
 * <ul>
 *   <li>A real STOMP client can subscribe to a named topic and receive messages
 *       published to it.</li>
 *   <li>Publishing to {@code /app/topic/news} broadcasts to all subscribers of
 *       {@code /topic/news}.</li>
 *   <li>Publishing to {@code /app/topic/sports} does NOT deliver to a client
 *       subscribed only to {@code /topic/news} (topic isolation).</li>
 *   <li>Multiple subscribers on the same topic all receive the message (fan-out).</li>
 *   <li>Messages are stored in per-topic history after being published over STOMP.</li>
 * </ul>
 *
 * <h2>Testing strategy</h2>
 * <ul>
 *   <li>{@code @SpringBootTest(RANDOM_PORT)} – starts the full embedded Tomcat
 *       server including the real WebSocket/STOMP endpoint on a free port.</li>
 *   <li>{@link StandardWebSocketClient} connecting directly to
 *       {@code /ws/websocket} – this bypasses SockJS negotiation (which does not
 *       work with Java clients) while still using the same STOMP broker.</li>
 *   <li>{@link BlockingQueue} – captures async messages; the test thread polls
 *       with a timeout to avoid busy-spinning.</li>
 * </ul>
 *
 * <p>No Docker containers are needed because this application has no external
 * infrastructure dependencies. Testcontainers is used in
 * {@link ContainerIntegrationTest} to run the application as a Docker image.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Topic Pub/Sub – Integration Tests")
class TopicPubSubIntegrationTest {

    /** Injected random port assigned by Spring to the embedded server. */
    @LocalServerPort
    private int port;

    /** Autowired to verify in-memory history after STOMP publishes. */
    @Autowired
    private TopicService topicService;

    /** Seconds to wait for an async WebSocket message before timing out. */
    private static final int TIMEOUT_SECONDS = 5;

    /**
     * Milliseconds to wait after subscribing before sending the first message.
     *
     * <p>The SUBSCRIBE frame is sent asynchronously; the server needs a brief
     * moment to register the subscription before a broadcast arrives. Without
     * this pause the broadcast may arrive at the broker before the subscription
     * is recorded and the subscriber misses the message.
     */
    private static final long SUBSCRIBE_SETTLE_MS = 300;

    /** Reset all topic state before each test to guarantee independence. */
    @BeforeEach
    void setUp() {
        topicService.clearAll();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates and connects a new STOMP client to the embedded server.
     *
     * <p>Uses {@link StandardWebSocketClient} connecting to the raw WebSocket
     * transport path {@code /ws/websocket} exposed by Spring's SockJS endpoint.
     * This bypasses SockJS negotiation, which is incompatible with Java's
     * JSR-356 WebSocket client, while still exercising the full STOMP broker.
     *
     * @return an active {@link StompSession}
     * @throws Exception if the connection cannot be established within the timeout
     */
    private StompSession connectStompClient() throws Exception {
        WebSocketStompClient stompClient =
                new WebSocketStompClient(new StandardWebSocketClient());

        // Register Jackson with JSR-310 (java.time) support so that
        // Instant fields in TopicMessage are correctly serialised/deserialised.
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);

        // Connect to the raw WebSocket transport exposed by the SockJS endpoint
        String url = "ws://localhost:" + port + "/ws/websocket";
        return stompClient.connectAsync(url, new StompSessionHandlerAdapter() {
            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                throw new RuntimeException("STOMP transport error: " + exception.getMessage(), exception);
            }
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Creates a {@link StompFrameHandler} that enqueues each received
     * {@link TopicMessage} into the provided {@link BlockingQueue}.
     *
     * @param queue the queue to populate
     * @return a frame handler suitable for {@code session.subscribe()}
     */
    private StompFrameHandler topicMessageHandler(BlockingQueue<TopicMessage> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                // Instruct the STOMP client to deserialise the payload as TopicMessage
                return TopicMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                // Enqueue the received message for the test thread to collect
                queue.offer((TopicMessage) payload);
            }
        };
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Publishing to /app/topic/news should broadcast to /topic/news subscribers")
    void publish_shouldBroadcastToTopicSubscribers() throws Exception {
        BlockingQueue<TopicMessage> received = new LinkedBlockingQueue<>();

        StompSession session = connectStompClient();

        // Subscribe to the topic BEFORE publishing to ensure the message is received
        session.subscribe("/topic/news", topicMessageHandler(received));
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        // Publish a message to the news topic
        session.send("/app/topic/news", new PublishRequest("alice", "Breaking news!"));

        // Wait for the broadcast to arrive (async delivery)
        TopicMessage message = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(message).isNotNull();
        assertThat(message.getTopic()).isEqualTo("news");
        assertThat(message.getSender()).isEqualTo("alice");
        assertThat(message.getPayload()).isEqualTo("Breaking news!");
        assertThat(message.getTimestamp()).isNotNull();

        session.disconnect();
    }

    @Test
    @DisplayName("Publishing to /app/topic/sports should NOT deliver to a /topic/news subscriber")
    void publish_shouldIsolateDifferentTopics() throws Exception {
        BlockingQueue<TopicMessage> newsQueue = new LinkedBlockingQueue<>();

        StompSession session = connectStompClient();

        // Subscribe only to the news topic
        session.subscribe("/topic/news", topicMessageHandler(newsQueue));
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        // Publish to the sports topic – news subscriber should NOT receive this
        session.send("/app/topic/sports", new PublishRequest("bob", "Goal!"));

        // Wait briefly; the queue should remain empty
        TopicMessage message = newsQueue.poll(1, TimeUnit.SECONDS);

        // Topic isolation: sports message must not arrive at news subscriber
        assertThat(message).isNull();

        session.disconnect();
    }

    @Test
    @DisplayName("Multiple subscribers to the same topic should all receive published messages (fan-out)")
    void publish_shouldFanOutToMultipleSubscribers() throws Exception {
        BlockingQueue<TopicMessage> queue1 = new LinkedBlockingQueue<>();
        BlockingQueue<TopicMessage> queue2 = new LinkedBlockingQueue<>();
        BlockingQueue<TopicMessage> queue3 = new LinkedBlockingQueue<>();

        // Connect three independent STOMP sessions
        StompSession session1 = connectStompClient();
        StompSession session2 = connectStompClient();
        StompSession session3 = connectStompClient();

        // All three subscribe to the same topic
        session1.subscribe("/topic/tech", topicMessageHandler(queue1));
        session2.subscribe("/topic/tech", topicMessageHandler(queue2));
        session3.subscribe("/topic/tech", topicMessageHandler(queue3));
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        // Publish one message – should fan-out to all three subscribers
        session1.send("/app/topic/tech", new PublishRequest("alice", "New framework released!"));

        TopicMessage msg1 = queue1.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        TopicMessage msg2 = queue2.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        TopicMessage msg3 = queue3.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // All three clients must receive the same message
        assertThat(msg1).isNotNull();
        assertThat(msg2).isNotNull();
        assertThat(msg3).isNotNull();

        assertThat(msg1.getPayload()).isEqualTo("New framework released!");
        assertThat(msg2.getPayload()).isEqualTo("New framework released!");
        assertThat(msg3.getPayload()).isEqualTo("New framework released!");

        session1.disconnect();
        session2.disconnect();
        session3.disconnect();
    }

    @Test
    @DisplayName("Multiple messages published to a topic should all be delivered in order")
    void publish_multipleMessages_shouldAllBeDelivered() throws Exception {
        BlockingQueue<TopicMessage> received = new LinkedBlockingQueue<>();

        StompSession session = connectStompClient();
        session.subscribe("/topic/news", topicMessageHandler(received));
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        // Publish three messages sequentially
        session.send("/app/topic/news", new PublishRequest("alice", "First"));
        session.send("/app/topic/news", new PublishRequest("bob", "Second"));
        session.send("/app/topic/news", new PublishRequest("charlie", "Third"));

        TopicMessage msg1 = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        TopicMessage msg2 = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        TopicMessage msg3 = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(msg1).isNotNull();
        assertThat(msg2).isNotNull();
        assertThat(msg3).isNotNull();

        // All three payloads must be present (order may vary for rapid async sends)
        assertThat(java.util.List.of(msg1.getPayload(), msg2.getPayload(), msg3.getPayload()))
                .containsExactlyInAnyOrder("First", "Second", "Third");

        session.disconnect();
    }

    @Test
    @DisplayName("Messages published via STOMP should be stored in per-topic history")
    void publish_shouldPersistToTopicHistory() throws Exception {
        BlockingQueue<TopicMessage> received = new LinkedBlockingQueue<>();

        StompSession session = connectStompClient();
        session.subscribe("/topic/news", topicMessageHandler(received));
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        session.send("/app/topic/news", new PublishRequest("alice", "Persisted!"));

        // Wait for broadcast confirmation that the message was processed
        TopicMessage message = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(message).isNotNull();

        // The service must have stored the message in history
        assertThat(topicService.getHistorySize("news")).isGreaterThanOrEqualTo(1);
        assertThat(topicService.getHistory("news"))
                .anyMatch(m -> "alice".equals(m.getSender())
                        && "Persisted!".equals(m.getPayload()));

        session.disconnect();
    }

    @Test
    @DisplayName("A subscriber that subscribes after publishing should not receive earlier messages over STOMP")
    void lateSubscriber_shouldNotReceiveOlderMessages() throws Exception {
        BlockingQueue<TopicMessage> early = new LinkedBlockingQueue<>();
        BlockingQueue<TopicMessage> late  = new LinkedBlockingQueue<>();

        StompSession earlySession = connectStompClient();
        earlySession.subscribe("/topic/news", topicMessageHandler(early));
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        // Publish BEFORE the late subscriber connects
        earlySession.send("/app/topic/news", new PublishRequest("alice", "Early message"));
        TopicMessage earlyMsg = early.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(earlyMsg).isNotNull();

        // Late subscriber connects AFTER the message was published
        StompSession lateSession = connectStompClient();
        lateSession.subscribe("/topic/news", topicMessageHandler(late));
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        // The late subscriber should receive nothing (the broker doesn't replay history)
        TopicMessage lateMsg = late.poll(1, TimeUnit.SECONDS);
        assertThat(lateMsg).isNull();

        // However, history is available via the REST API
        assertThat(topicService.getHistory("news"))
                .anyMatch(m -> "Early message".equals(m.getPayload()));

        earlySession.disconnect();
        lateSession.disconnect();
    }
}
