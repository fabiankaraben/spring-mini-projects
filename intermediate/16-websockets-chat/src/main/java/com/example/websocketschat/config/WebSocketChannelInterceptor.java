package com.example.websocketschat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Channel interceptor that hooks into the inbound STOMP message channel.
 *
 * <h2>Purpose</h2>
 * <p>When a client sends a STOMP SEND frame to {@code /app/chat.join}, the
 * {@code sender} field is available in the JSON payload but is not stored
 * anywhere accessible to the disconnect handler. This interceptor watches for
 * incoming messages and stores the sender's username in the WebSocket session
 * attributes so that {@link com.example.websocketschat.listener.WebSocketEventListener}
 * can retrieve it later when the session disconnects.
 *
 * <h2>Registration</h2>
 * <p>This bean is registered as a channel interceptor in
 * {@link WebSocketConfig#configureClientInboundChannel} (see that class for
 * the wiring). Spring invokes {@link #preSend} for every inbound message
 * before it is dispatched to a {@code @MessageMapping} handler.
 *
 * <h2>Thread safety</h2>
 * <p>Session attribute maps are per-session and are not shared across threads,
 * so no additional synchronisation is needed.
 */
@Component
public class WebSocketChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketChannelInterceptor.class);

    /**
     * Intercepts every inbound STOMP message before delivery.
     *
     * <p>When the STOMP command is CONNECT, we log the new connection.
     * When the command is SEND to the join destination, we do nothing here
     * because the username is extracted from the JSON body in the controller.
     * This interceptor is mainly a hook point for CONNECT-level processing.
     *
     * @param message the raw inbound STOMP message
     * @param channel the channel the message is being sent on
     * @return the (unmodified) message to continue processing
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // Wrap the message in an accessor for easy STOMP header access
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // A new WebSocket client has initiated a STOMP session
            log.info("New STOMP client connected. SessionId: {}", accessor.getSessionId());
        }

        // Always return the message unchanged so the pipeline continues
        return message;
    }
}
