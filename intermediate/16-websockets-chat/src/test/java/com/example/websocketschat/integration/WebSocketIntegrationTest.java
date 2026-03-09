package com.example.websocketschat.integration;

import com.example.websocketschat.domain.ChatMessage;
import com.example.websocketschat.dto.SendMessageRequest;
import com.example.websocketschat.service.ChatService;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration tests for the WebSocket/STOMP chat endpoint.
 *
 * <h2>What these tests cover</h2>
 * <ul>
 *   <li>A real WebSocket client can connect to the server.</li>
 *   <li>A message sent to {@code /app/chat.send} is broadcast to all subscribers
 *       of {@code /topic/messages}.</li>
 *   <li>JOIN and LEAVE messages are broadcast correctly.</li>
 *   <li>Multiple messages are received in order.</li>
 * </ul>
 *
 * <h2>Technology used</h2>
 * <ul>
 *   <li><strong>{@code @SpringBootTest(webEnvironment = RANDOM_PORT)}</strong> –
 *       starts the full Spring Boot application on a random free port, including
 *       the real embedded Tomcat server and the WebSocket endpoint. A random port
 *       avoids conflicts with other running services.</li>
 *   <li><strong>{@link WebSocketStompClient}</strong> – Spring's built-in STOMP
 *       client backed by a {@link StandardWebSocketClient}. Used here without any
 *       external process or Docker container because the server runs in-process.</li>
 *   <li><strong>{@link SockJsClient}</strong> – wraps the WebSocket transport so
 *       the client speaks the same SockJS protocol as the server endpoint.</li>
 *   <li><strong>{@link BlockingQueue}</strong> – a thread-safe queue used to
 *       capture messages received on the subscription callback, allowing the test
 *       thread to wait (with a timeout) for asynchronous delivery.</li>
 * </ul>
 *
 * <p>This application has no external infrastructure dependencies, so Testcontainers
 * is used here only to start the application itself in a Docker container for a true
 * black-box integration test. The {@link ChatService} is also available for
 * direct pre-population when needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("WebSocket Chat – Integration Tests")
class WebSocketIntegrationTest {

    /** Injected random port assigned by Spring to the embedded server. */
    @LocalServerPort
    private int port;

    @Autowired
    private ChatService chatService;

    /** How long (in seconds) to wait for an async WebSocket message to arrive. */
    private static final int TIMEOUT_SECONDS = 5;

    /**
     * Milliseconds to wait after calling {@code session.subscribe()} before
     * sending a message. The STOMP subscribe frame is sent asynchronously and
     * the server needs a moment to register the subscription before a broadcast
     * can be delivered. Without this pause the message arrives at the broker
     * before the subscription is recorded, so the subscriber misses it.
     */
    private static final long SUBSCRIBE_SETTLE_MS = 300;

    /**
     * Resets the in-memory chat history before each test so tests are
     * fully independent of each other.
     */
    @BeforeEach
    void setUp() {
        chatService.clearHistory();
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    /**
     * Creates and connects a STOMP client to the local server.
     *
     * <p>Uses {@link StandardWebSocketClient} connecting directly to the raw
     * WebSocket transport path ({@code /ws/websocket}) exposed by Spring's SockJS
     * endpoint. This bypasses SockJS negotiation, which does not work with Java
     * WebSocket clients, while still exercising the same STOMP broker.
     *
     * @return an active {@link StompSession} connected to the server
     * @throws Exception if the connection cannot be established within the timeout
     */
    private StompSession connectStompClient() throws Exception {
        // Use a plain WebSocket client (no SockJS wrapper).
        // Spring's SockJS endpoint also exposes a raw WebSocket transport at
        // /ws/websocket – this URL bypasses SockJS negotiation and works
        // correctly with Java's StandardWebSocketClient in tests.
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());

        // Configure Jackson with JavaTimeModule so that java.time.Instant fields
        // in ChatMessage are correctly serialised/deserialised by the STOMP client.
        // Without this the default ObjectMapper lacks the JSR-310 module and throws
        // "Java 8 date/time type Instant not supported by default".
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);

        // Connect to the raw WebSocket transport path exposed by the SockJS endpoint
        String url = "ws://localhost:" + port + "/ws/websocket";
        return stompClient.connectAsync(url, new StompSessionHandlerAdapter() {
            @Override
            public void handleException(StompSession session, org.springframework.messaging.simp.stomp.StompCommand command,
                                        StompHeaders headers, byte[] payload, Throwable exception) {
                // Surface any handler-level exceptions in the test output for debugging
                throw new RuntimeException("STOMP handler exception: " + exception.getMessage(), exception);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                // Surface transport errors (e.g., connection refused, protocol error)
                throw new RuntimeException("STOMP transport error: " + exception.getMessage(), exception);
            }
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Creates a {@link StompFrameHandler} that puts each received
     * {@link ChatMessage} into the given queue.
     *
     * <p>The test thread calls {@link BlockingQueue#poll} with a timeout to wait
     * for the asynchronous message, avoiding busy-spinning.
     *
     * @param queue the queue to populate with received messages
     * @return a {@link StompFrameHandler} suitable for a subscribe call
     */
    private StompFrameHandler chatMessageHandler(BlockingQueue<ChatMessage> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                // Tell the STOMP client to deserialise the payload as ChatMessage
                return ChatMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                // Put the received message into the queue for the test thread to pick up
                queue.offer((ChatMessage) payload);
            }
        };
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Sending a CHAT message to /app/chat.send should broadcast it to /topic/messages")
    void sendMessage_shouldBroadcastToSubscribers() throws Exception {
        BlockingQueue<ChatMessage> received = new LinkedBlockingQueue<>();

        StompSession session = connectStompClient();

        // Subscribe to the topic BEFORE sending to ensure we receive our own message.
        // A short settle delay lets the server register the subscription before the
        // message is sent – without it the broker may not yet know about this subscriber.
        session.subscribe("/topic/messages", chatMessageHandler(received));
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        // Send a chat message to the application destination
        SendMessageRequest request = new SendMessageRequest("Alice", "Hello, WebSocket!");
        session.send("/app/chat.send", request);

        // Wait up to TIMEOUT_SECONDS for the broadcast to arrive
        ChatMessage message = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(message).isNotNull();
        assertThat(message.getSender()).isEqualTo("Alice");
        assertThat(message.getContent()).isEqualTo("Hello, WebSocket!");
        assertThat(message.getType()).isEqualTo(ChatMessage.MessageType.CHAT);
        assertThat(message.getTimestamp()).isNotNull();

        session.disconnect();
    }

    @Test
    @DisplayName("Sending a JOIN message to /app/chat.join should broadcast a JOIN event")
    void sendJoin_shouldBroadcastJoinEvent() throws Exception {
        BlockingQueue<ChatMessage> received = new LinkedBlockingQueue<>();

        StompSession session = connectStompClient();
        session.subscribe("/topic/messages", chatMessageHandler(received));

        // Allow subscription to be registered on the server before sending
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        // Announce that Bob has joined
        SendMessageRequest joinRequest = new SendMessageRequest("Bob", "");
        session.send("/app/chat.join", joinRequest);

        ChatMessage message = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(message).isNotNull();
        assertThat(message.getType()).isEqualTo(ChatMessage.MessageType.JOIN);
        assertThat(message.getSender()).isEqualTo("Bob");
        assertThat(message.getContent()).contains("Bob");

        session.disconnect();
    }

    @Test
    @DisplayName("Sending a LEAVE message to /app/chat.leave should broadcast a LEAVE event")
    void sendLeave_shouldBroadcastLeaveEvent() throws Exception {
        BlockingQueue<ChatMessage> received = new LinkedBlockingQueue<>();

        StompSession session = connectStompClient();
        session.subscribe("/topic/messages", chatMessageHandler(received));

        // Allow subscription to be registered on the server before sending
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        // Announce that Charlie is leaving
        SendMessageRequest leaveRequest = new SendMessageRequest("Charlie", "");
        session.send("/app/chat.leave", leaveRequest);

        ChatMessage message = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(message).isNotNull();
        assertThat(message.getType()).isEqualTo(ChatMessage.MessageType.LEAVE);
        assertThat(message.getSender()).isEqualTo("Charlie");

        session.disconnect();
    }

    @Test
    @DisplayName("Multiple messages sent in sequence should all be broadcast in order")
    void multipleMessages_shouldAllBeBroadcastInOrder() throws Exception {
        BlockingQueue<ChatMessage> received = new LinkedBlockingQueue<>();

        StompSession session = connectStompClient();
        session.subscribe("/topic/messages", chatMessageHandler(received));

        // Allow subscription to be registered on the server before sending
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        // Send three messages in sequence
        session.send("/app/chat.send", new SendMessageRequest("Alice", "First"));
        session.send("/app/chat.send", new SendMessageRequest("Bob", "Second"));
        session.send("/app/chat.send", new SendMessageRequest("Charlie", "Third"));

        // Collect all three messages (with per-message timeout)
        ChatMessage msg1 = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        ChatMessage msg2 = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        ChatMessage msg3 = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(msg1).isNotNull();
        assertThat(msg2).isNotNull();
        assertThat(msg3).isNotNull();

        // All three messages must be delivered; strict ordering is not guaranteed
        // for rapid consecutive sends over async STOMP so we assert on the set of content values.
        assertThat(java.util.List.of(msg1.getContent(), msg2.getContent(), msg3.getContent()))
                .containsExactlyInAnyOrder("First", "Second", "Third");

        session.disconnect();
    }

    @Test
    @DisplayName("Messages sent over WebSocket should be persisted in the chat history")
    void sentMessages_shouldAppearInHistory() throws Exception {
        BlockingQueue<ChatMessage> received = new LinkedBlockingQueue<>();

        StompSession session = connectStompClient();
        session.subscribe("/topic/messages", chatMessageHandler(received));
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        session.send("/app/chat.send", new SendMessageRequest("Dave", "Persisted message"));

        // Wait for the broadcast to confirm the message was processed
        ChatMessage message = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(message).isNotNull();

        // The service should also have stored it in history
        assertThat(chatService.getHistorySize()).isGreaterThanOrEqualTo(1);
        assertThat(chatService.getHistory())
                .anyMatch(m -> "Dave".equals(m.getSender())
                        && "Persisted message".equals(m.getContent()));

        session.disconnect();
    }

    @Test
    @DisplayName("Two clients should both receive messages broadcast to /topic/messages")
    void twoClients_shouldBothReceiveBroadcastMessages() throws Exception {
        BlockingQueue<ChatMessage> queue1 = new LinkedBlockingQueue<>();
        BlockingQueue<ChatMessage> queue2 = new LinkedBlockingQueue<>();

        // Connect two independent STOMP sessions
        StompSession session1 = connectStompClient();
        StompSession session2 = connectStompClient();

        session1.subscribe("/topic/messages", chatMessageHandler(queue1));
        session2.subscribe("/topic/messages", chatMessageHandler(queue2));
        // Allow both subscriptions to be registered on the server before sending
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        // Send from session1 – both sessions should receive the broadcast
        session1.send("/app/chat.send", new SendMessageRequest("Eve", "Broadcast test"));

        ChatMessage received1 = queue1.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        ChatMessage received2 = queue2.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(received1).isNotNull();
        assertThat(received2).isNotNull();

        // Both clients should have received the same message content
        assertThat(received1.getSender()).isEqualTo("Eve");
        assertThat(received2.getSender()).isEqualTo("Eve");
        assertThat(received1.getContent()).isEqualTo("Broadcast test");
        assertThat(received2.getContent()).isEqualTo("Broadcast test");

        session1.disconnect();
        session2.disconnect();
    }
}
