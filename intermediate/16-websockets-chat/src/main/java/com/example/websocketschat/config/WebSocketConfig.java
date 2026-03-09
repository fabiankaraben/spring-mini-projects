package com.example.websocketschat.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP configuration for the chat application.
 *
 * <h2>Key concepts</h2>
 * <ul>
 *   <li><strong>STOMP</strong> – Simple Text Oriented Messaging Protocol. A
 *       sub-protocol layered on top of WebSocket that adds concepts like
 *       destinations (pub/sub channels), receipts, and heart-beats. Using STOMP
 *       means clients and the server speak a common, well-defined message format
 *       instead of raw WebSocket frames.</li>
 *   <li><strong>SockJS</strong> – A JavaScript library that provides a
 *       WebSocket-like API with automatic fallback to HTTP long-polling when
 *       WebSocket is unavailable (e.g., corporate proxies). Enabling it here
 *       allows browser clients to use the SockJS library for maximum
 *       compatibility.</li>
 *   <li><strong>Simple broker</strong> – Spring's built-in in-memory message
 *       broker. It keeps a registry of subscriptions and routes messages to
 *       matching destinations. For production you could replace this with a full
 *       broker (ActiveMQ, RabbitMQ) via {@code enableStompBrokerRelay}.</li>
 * </ul>
 *
 * <h2>Message flow</h2>
 * <pre>
 *   Client --SEND /app/chat.send --> ChatController.handleSend()
 *         --> SimpMessagingTemplate.convertAndSend("/topic/messages")
 *         --> Simple broker delivers to all subscribers of /topic/messages
 *         --> Each subscribed client receives the ChatMessage
 * </pre>
 */
@Configuration
@EnableWebSocketMessageBroker // Enables WebSocket message handling backed by a message broker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Channel interceptor that logs CONNECT events and stores session attributes.
     * Spring injects this automatically via constructor injection.
     */
    private final WebSocketChannelInterceptor channelInterceptor;

    /**
     * Constructor injection of the channel interceptor.
     *
     * @param channelInterceptor the interceptor to register on the inbound channel
     */
    @Autowired
    public WebSocketConfig(WebSocketChannelInterceptor channelInterceptor) {
        this.channelInterceptor = channelInterceptor;
    }

    /**
     * Configures the message broker.
     *
     * <ul>
     *   <li>{@code enableSimpleBroker("/topic")} – activates the in-memory
     *       broker for destinations that start with {@code /topic}. Subscribers
     *       to {@code /topic/messages} will receive all broadcast messages.</li>
     *   <li>{@code setApplicationDestinationPrefixes("/app")} – any message
     *       sent from a client to a destination beginning with {@code /app} is
     *       routed to a {@code @MessageMapping} method in a controller rather
     *       than directly to the broker.</li>
     * </ul>
     *
     * @param config the broker registry to configure
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable an in-memory STOMP broker on the /topic prefix.
        // All subscribers to /topic/** will receive messages forwarded here.
        config.enableSimpleBroker("/topic");

        // Messages sent to destinations starting with /app are handled by
        // @MessageMapping methods in our controllers.
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Registers the channel interceptor on the client inbound channel.
     *
     * <p>Every STOMP message arriving from a client passes through this
     * channel before reaching a {@code @MessageMapping} handler. The
     * interceptor can inspect or modify headers and session attributes.
     *
     * @param registration the inbound channel configuration
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Add our interceptor so it sees every inbound STOMP frame
        registration.interceptors(channelInterceptor);
    }

    /**
     * Registers the STOMP WebSocket endpoint that clients connect to.
     *
     * <p>Clients (browser or test code) establish their WebSocket connection
     * to {@code ws://host:port/ws}. By enabling SockJS, clients that cannot
     * use WebSocket can fall back to HTTP streaming or long-polling via
     * {@code http://host:port/ws}.
     *
     * @param registry the registry for STOMP endpoints
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
            // The URL path clients connect to when opening a WebSocket connection
            .addEndpoint("/ws")
            // Allow all origins (suitable for dev; restrict in production)
            .setAllowedOriginPatterns("*")
            // Enable SockJS fallback transport for environments without WebSocket
            .withSockJS();
    }
}
