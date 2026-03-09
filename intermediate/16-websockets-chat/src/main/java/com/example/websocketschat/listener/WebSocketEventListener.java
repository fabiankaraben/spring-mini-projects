package com.example.websocketschat.listener;

import com.example.websocketschat.domain.ChatMessage;
import com.example.websocketschat.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Spring application event listener for WebSocket session lifecycle events.
 *
 * <h2>Why listen to disconnect events?</h2>
 * <p>When a client closes its WebSocket connection abruptly (e.g., browser tab
 * closed, network drop), no STOMP SEND to {@code /app/chat.leave} is triggered.
 * Spring publishes a {@link SessionDisconnectEvent} when the underlying WebSocket
 * session terminates, allowing the server to broadcast a LEAVE notification on
 * behalf of the disconnected user.
 *
 * <h2>Username extraction</h2>
 * <p>The username is stored in the WebSocket session attributes when the client
 * sends a JOIN message. The {@link StompHeaderAccessor} provides access to those
 * session attributes so we can retrieve the username here without an external
 * session store.
 *
 * <h2>Integration with the broker</h2>
 * <p>Unlike {@code @MessageMapping} controllers, event listeners do not have an
 * implicit {@code @SendTo}. We therefore inject {@link SimpMessageSendingOperations}
 * and call {@code convertAndSend} explicitly to publish the LEAVE message to
 * {@code /topic/messages}.
 */
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    /**
     * Spring's STOMP messaging template used to send messages to broker
     * destinations programmatically (outside of {@code @MessageMapping} methods).
     */
    private final SimpMessageSendingOperations messagingTemplate;

    /** Chat service – builds LEAVE messages with proper timestamps. */
    private final ChatService chatService;

    /**
     * Constructor injection.
     *
     * @param messagingTemplate STOMP messaging template
     * @param chatService       chat domain service
     */
    public WebSocketEventListener(SimpMessageSendingOperations messagingTemplate,
                                  ChatService chatService) {
        this.messagingTemplate = messagingTemplate;
        this.chatService = chatService;
    }

    /**
     * Handles WebSocket session disconnection.
     *
     * <p>When a client disconnects (gracefully or abruptly), this method:
     * <ol>
     *   <li>Extracts the username from the session attributes (stored during JOIN).</li>
     *   <li>Builds a LEAVE {@link ChatMessage} via {@link ChatService}.</li>
     *   <li>Broadcasts it to {@code /topic/messages} so all remaining clients
     *       see the departure notification.</li>
     * </ol>
     *
     * <p>If no username is found in the session (i.e., the client disconnected
     * before ever sending a JOIN), the event is silently ignored.
     *
     * @param event the Spring disconnect event carrying STOMP session info
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        // Wrap the raw message in a STOMP-aware accessor to read session attributes
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        // Retrieve the username that was stored when the client sent a JOIN
        String username = (String) headerAccessor.getSessionAttributes().get("username");

        if (username != null) {
            log.info("WebSocket client disconnected: {}", username);

            // Build a LEAVE system message with a server-assigned timestamp
            ChatMessage leaveMessage = chatService.buildLeaveMessage(username);

            // Broadcast the LEAVE notification to all remaining subscribers
            messagingTemplate.convertAndSend("/topic/messages", leaveMessage);
        }
    }
}
