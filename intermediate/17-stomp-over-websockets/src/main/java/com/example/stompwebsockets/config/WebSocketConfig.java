package com.example.stompwebsockets.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP configuration for the pub/sub message routing application.
 *
 * <h2>STOMP protocol recap</h2>
 * <p>STOMP (Simple Text Oriented Messaging Protocol) is a sub-protocol layered
 * on top of WebSocket. It defines a set of frames analogous to HTTP methods:
 * <ul>
 *   <li><strong>CONNECT</strong> – establishes the STOMP session.</li>
 *   <li><strong>SUBSCRIBE destination=/topic/news</strong> – registers interest
 *       in messages published to that destination.</li>
 *   <li><strong>SEND destination=/app/topic/news</strong> – sends a message
 *       to the server-side handler.</li>
 *   <li><strong>UNSUBSCRIBE</strong> – cancels a subscription.</li>
 *   <li><strong>DISCONNECT</strong> – ends the session.</li>
 * </ul>
 *
 * <h2>Destination prefixes used in this application</h2>
 * <ul>
 *   <li>{@code /app} – messages sent here are routed to {@code @MessageMapping}
 *       controller methods for processing before being broadcast.</li>
 *   <li>{@code /topic} – the in-memory broker handles subscriptions here;
 *       many clients can subscribe to the same topic and all receive every
 *       message published there (classic pub/sub fan-out).</li>
 *   <li>{@code /user} – Spring prefixes this automatically when using
 *       {@code SimpMessageSendingOperations.convertAndSendToUser()};
 *       messages are routed exclusively to the named user's session(s).</li>
 * </ul>
 *
 * <h2>SockJS fallback</h2>
 * <p>Enabling SockJS on the endpoint allows browser clients to fall back from
 * WebSocket to HTTP long-polling when the environment (e.g., corporate proxies)
 * blocks WebSocket upgrades.
 */
@Configuration
@EnableWebSocketMessageBroker // Activates the STOMP message broker infrastructure
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Configures the message broker routing rules.
     *
     * <ul>
     *   <li>{@code enableSimpleBroker("/topic", "/queue")} – activates the
     *       in-memory broker on both prefixes. {@code /topic} is used for
     *       broadcast pub/sub topics; {@code /queue} is used for per-user
     *       private message queues managed by the {@code /user} feature.</li>
     *   <li>{@code setApplicationDestinationPrefixes("/app")} – messages sent
     *       to {@code /app/**} are dispatched to {@code @MessageMapping} methods
     *       in controllers rather than going directly to the broker.</li>
     *   <li>{@code setUserDestinationPrefix("/user")} – enables Spring's
     *       user-destination support. A message sent to
     *       {@code /user/{username}/queue/private} is delivered only to sessions
     *       associated with that username.</li>
     * </ul>
     *
     * @param config the registry to configure
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable the in-memory broker on /topic (broadcast) and /queue (per-user)
        config.enableSimpleBroker("/topic", "/queue");

        // Application-level destinations: handled by @MessageMapping controllers
        config.setApplicationDestinationPrefixes("/app");

        // User-specific destination prefix for private messaging
        config.setUserDestinationPrefix("/user");
    }

    /**
     * Registers the STOMP WebSocket endpoint.
     *
     * <p>Clients connect to {@code ws://host:port/ws} (raw WebSocket) or
     * use the SockJS client at {@code http://host:port/ws} for fallback support.
     *
     * @param registry the registry for STOMP endpoints
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
            // The path clients use when opening their WebSocket/SockJS connection
            .addEndpoint("/ws")
            // Allow all origins – suitable for development; restrict in production
            .setAllowedOriginPatterns("*")
            // Enable SockJS fallback for clients in restricted network environments
            .withSockJS();
    }
}
